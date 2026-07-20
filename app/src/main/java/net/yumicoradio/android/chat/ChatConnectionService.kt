package net.yumicoradio.android.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(
            ONGOING_ID,
            ongoingNotification(),
            // Android 14 wants a declared type. Remote messaging is what this is.
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            } else {
                0
            },
        )
        watch()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun watch() {
        val yumi = application as YumiApp
        val repo = yumi.chat

        watcher = scope.launch {
            // `drop(1)` skips the buffer as it stands when the service starts: re-notifying the
            // whole backlog the moment the screen turns off would be a burst of stale alerts.
            combine(
                repo.state.drop(1),
                repo.pm.drop(1),
                yumi.prefs.notificationMode,
                repo.nick,
            ) { state, pm, mode, nick ->
                Triple(state.buffer(state.active).lastOrNull(), pm, mode to nick)
            }.collect { (lastChannelMessage, pmState, modeAndNick) ->
                val (mode, nickState) = modeAndNick
                val me = (nickState as? NickState.Joined)?.nickname.orEmpty()

                lastChannelMessage?.let { notifyIfAllowed(it, mode, me, isPm = false) }
                pmState.active?.let { active ->
                    pmState.messages(active).lastOrNull()
                        ?.let { notifyIfAllowed(it, mode, me, isPm = true) }
                }
                pmState.unread.forEach { nick ->
                    pmState.messages(nick).lastOrNull()
                        ?.let { notifyIfAllowed(it, mode, me, isPm = true) }
                }
            }
        }
    }

    private var lastNotified: String? = null

    private fun notifyIfAllowed(
        message: ChatMessage,
        mode: NotificationMode,
        me: String,
        isPm: Boolean,
    ) {
        if (!NotificationPolicy.shouldNotify(message, mode, me, isPm)) return

        // Flows re-emit for reasons unrelated to new text; without this the same line would
        // notify again every time anything else in the state changed.
        val fingerprint = "${message.user}|${message.text}|$isPm"
        if (fingerprint == lastNotified) return
        lastNotified = fingerprint

        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        val notification = NotificationCompat.Builder(this, MESSAGES_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(if (isPm) "${message.user} (private)" else message.user)
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching { manager.notify(message.user.hashCode(), notification) }
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
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
        private const val CONNECTION_CHANNEL = "chat_connection"
        private const val MESSAGES_CHANNEL = "chat_messages"

        fun start(context: Context) {
            val intent = Intent(context, ChatConnectionService::class.java)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ChatConnectionService::class.java))
        }
    }
}
