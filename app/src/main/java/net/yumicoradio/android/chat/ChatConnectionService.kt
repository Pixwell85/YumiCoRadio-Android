// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.yumicoradio.android.R
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.chat.model.ChatMessage
import net.yumicoradio.android.chat.model.NickState
import net.yumicoradio.android.ui.MainActivity

/**
 * Holds the chat connection while the app is in the background, and raises notifications for what
 * arrives.
 *
 * A foreground service is the only way Android will keep a socket alive off-screen, and it comes
 * with a permanent notification whether we want one or not — so the user opts in from the chat
 * options rather than having this sprung on them.
 *
 * The connection itself lives in the application-scoped [ChatRepository]; this service exists to
 * keep the process alive and to watch the flows, not to own the socket.
 */
class ChatConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob())
    private var watcher: Job? = null
    private var reliabilityWatcher: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var cpuLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        val notification = ongoingNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                ONGOING_ID,
                notification,
                // Android 14 wants the type declared in the manifest and supplied at promotion.
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING,
            )
        } else {
            startForeground(ONGOING_ID, notification)
        }
        BackgroundProtectionMonitor.update {
            it.copy(serviceRunning = true, lastError = null)
        }
        acquireLocks()
        watch()
        watchReliabilityMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        watcher?.cancel()
        reliabilityWatcher?.cancel()
        scope.cancel()
        releaseLocks()
        BackgroundProtectionMonitor.update {
            it.copy(serviceRunning = false, wifiLockHeld = false, cpuLockHeld = false)
        }
        super.onDestroy()
    }

    /**
     * Hold a Wi-Fi lock for the lifetime of the service.
     *
     * A partial wake lock used to sit here too, but device testing showed MIUI ignores it (the socket
     * still dropped screen-off), so it only cost battery — a CPU that never deep-sleeps — for nothing.
     * Dropped. The Wi-Fi lock stays: it is cheap and, on stacks that honour it, keeps the radio out of
     * power-save so the socket survives the screen going off. HIGH_PERF, not LOW_LATENCY — the latter
     * only applies foreground with the screen on, exactly when we do not need it. Reference counting
     * is off so a redundant [acquireLocks] can't stack holds.
     */
    private fun acquireLocks() {
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            runCatching {
                wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "$WAKE_TAG:wifi").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onSuccess { lock ->
                wifiLock = lock
                BackgroundProtectionMonitor.update {
                    it.copy(wifiLockHeld = lock.isHeld, lastError = null)
                }
            }.onFailure { error ->
                reportProtectionError("Wi-Fi lock", error)
            }
        }
    }

    private fun watchReliabilityMode() {
        val yumi = application as YumiApp
        reliabilityWatcher = scope.launch {
            combine(
                yumi.prefs.maximumReliability,
                yumi.prefs.stayConnected,
                yumi.chat.nick,
            ) { maximum, stay, nick ->
                shouldHoldCpuWakeLock(maximum, stay, nick.hasSession)
            }
                .distinctUntilChanged()
                .collect(::setCpuLock)
        }
    }

    // Maximum reliability intentionally keeps this lock for the foreground service lifetime.
    // A timeout would make the protection silently expire during an overnight session; onDestroy
    // and the preference/session watcher both release it explicitly.
    @SuppressLint("Wakelock", "WakelockTimeout")
    private fun setCpuLock(enabled: Boolean) {
        if (enabled && cpuLock?.isHeld != true) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            runCatching {
                pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$WAKE_TAG:cpu").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onSuccess { lock ->
                cpuLock = lock
                BackgroundProtectionMonitor.update {
                    it.copy(cpuLockHeld = lock.isHeld, lastError = null)
                }
            }.onFailure { error ->
                reportProtectionError("CPU lock", error)
            }
        } else if (!enabled) {
            cpuLock?.let { lock -> if (lock.isHeld) runCatching { lock.release() } }
            cpuLock = null
            BackgroundProtectionMonitor.update { it.copy(cpuLockHeld = false) }
        }
    }

    private fun reportProtectionError(label: String, error: Throwable) {
        Log.w(TAG, "$label unavailable", error)
        BackgroundProtectionMonitor.update {
            it.copy(lastError = "$label unavailable (${error.javaClass.simpleName})")
        }
    }

    private fun releaseLocks() {
        wifiLock?.let { if (it.isHeld) runCatching { it.release() } }
        wifiLock = null
        cpuLock?.let { if (it.isHeld) runCatching { it.release() } }
        cpuLock = null
        BackgroundProtectionMonitor.update {
            it.copy(wifiLockHeld = false, cpuLockHeld = false)
        }
    }

    private fun watch() {
        val yumi = application as YumiApp
        val repo = yumi.chat

        watcher = scope.launch {
            // The first combined value is whatever is already buffered when the service starts.
            // `combine` also only fires once *every* source has a value, so gating on it (rather
            // than `drop`ping the state/pm flows — which stalls entirely for a user who never gets
            // a PM) is what makes public-channel notifications work at all.
            var primed = false
            var seen = emptyMap<String, String>()
            combine(
                repo.state,
                repo.pm,
                yumi.prefs.notificationMode,
                repo.nick,
            ) { state, pm, mode, nick -> Snapshot(state, pm, mode, nick) }
                .collect { snap ->
                    val me = (snap.nick as? NickState.Joined)?.nickname.orEmpty()

                    // The first combined value is the backlog as it already stands; record it as
                    // seen without notifying, then surface only what lands afterwards.
                    if (!primed) {
                        seen = ChatNotifications.seed(snap.state, snap.pm)
                        primed = true
                        return@collect
                    }

                    val decision = ChatNotifications.advance(seen, snap.state, snap.pm, snap.mode, me)
                    seen = decision.seen
                    // A foreground PM is already announced by the in-app ding (Shell); notifying it
                    // too would sound twice. Public/mention notifications are left as they were.
                    decision.toNotify
                        .filterNot { it.isPm && yumi.isForeground }
                        .forEach { notify(it.key, it.message, it.isPm) }
                }
        }
    }

    private data class Snapshot(
        val state: ChatState,
        val pm: PmState,
        val mode: NotificationMode,
        val nick: NickState,
    )

    private fun notify(key: String, message: ChatMessage, isPm: Boolean) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val notification = NotificationCompat.Builder(this, MESSAGES_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(if (isPm) "${message.user} (private)" else message.user)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Id keyed by conversation *and* sender: a public line and a PM from the same user, or two
        // different senders in a channel, would otherwise overwrite each other's notification.
        runCatching { manager.notify("$key|${message.user}".hashCode(), notification) }
    }

    private fun ongoingNotification(): Notification =
        NotificationCompat.Builder(this, CONNECTION_CHANNEL)
            // The station's star, not the platform's sync glyph — two arrows chasing each other say
            // "syncing", which is neither what this is nor recognisable as this app.
            .setSmallIcon(R.drawable.ic_notify_star)
            .setContentTitle("Live Chat connected")
            .setContentText("Staying connected in the background")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            // A request code distinct from the media notification's: without it the two content
            // intents (both bare MainActivity) share one PendingIntent slot and the media tap
            // inherits this OPEN_CHAT extra.
            MainActivity.REQ_OPEN_CHAT,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                // Every chat notification is about the chat, so tapping one lands on the Chat tab.
                .putExtra(MainActivity.EXTRA_OPEN_CHAT, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CONNECTION_CHANNEL,
                "Chat connection",
                // The permanent one is deliberately quiet: it is a status, not an event.
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                MESSAGES_CHANNEL,
                "Chat messages",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        private const val ONGOING_ID = 4201
        internal const val CONNECTION_CHANNEL = "chat_connection"
        internal const val MESSAGES_CHANNEL = "chat_messages"
        private const val WAKE_TAG = "yumicoradio:chat"
        private const val TAG = "ChatProtection"

        fun start(context: Context): Result<Unit> = runCatching {
            val intent = Intent(context, ChatConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Unit
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatConnectionService::class.java))
        }
    }
}
