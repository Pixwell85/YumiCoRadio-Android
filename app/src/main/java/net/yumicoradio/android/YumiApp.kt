// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.yumicoradio.android.chat.ChatConnectionService
import net.yumicoradio.android.chat.ChatRepository
import net.yumicoradio.android.chat.ChatSessionRestorer
import net.yumicoradio.android.chat.SecurePasswordStore
import net.yumicoradio.android.chat.shouldRunConnectionService
import net.yumicoradio.android.chat.GifDecoderKind
import net.yumicoradio.android.chat.gifDecoderKind
import net.yumicoradio.android.chat.model.NickState
import net.yumicoradio.android.data.PrefsStore
import net.yumicoradio.android.metadata.*

class YumiApp : Application(), ImageLoaderFactory {
    // Explicit Dispatchers.Default: the auto-away heartbeat's delay() must run on a real background
    // dispatcher, not be left to inference from a scope with no dispatcher element.
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val http by lazy { OkHttpClient() }
    lateinit var prefs: PrefsStore; private set
    lateinit var metadata: MetadataRepository; private set
    lateinit var chat: ChatRepository; private set

    // True while any activity is started (app on screen). The connection service reads it to skip a
    // PM notification the user would hear twice — the in-app ding already covers a foreground PM.
    @Volatile
    var isForeground: Boolean = false
        private set

    /**
     * Coil 2 does not discover its optional GIF decoders from the dependency alone. Owning the
     * singleton loader here makes the extension part of the real decode pipeline used by every
     * AsyncImage in the app while leaving Coil's normal bitmap components intact.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                when (gifDecoderKind(Build.VERSION.SDK_INT)) {
                    GifDecoderKind.IMAGE_DECODER -> add(ImageDecoderDecoder.Factory())
                    GifDecoderKind.GIF_DECODER -> add(GifDecoder.Factory())
                }
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsStore(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) { started++; isForeground = true }
            override fun onActivityStopped(activity: Activity) { if (--started <= 0) { started = 0; isForeground = false } }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        metadata = MetadataRepository(
            api = AzuraNowPlayingApi(http),
            scope = appScope,
        )
        // Application-scoped so navigating between screens does not disconnect — a screen-scoped
        // connection would broadcast a join/quit pair to every user on every visit.
        chat = ChatRepository(scope = appScope)

        // A foreground service makes process removal less likely, not impossible. Rehydrate the
        // application-scoped repository once when Android recreates us, before any screen needs to
        // exist. The encrypted password is primed before connect so a reserved nickname's first join
        // carries it.
        appScope.launch {
            chat.setSeparatePresenceActivity(prefs.chatSeparatePresence.first())
            val restorer = ChatSessionRestorer(
                loadPassword = SecurePasswordStore(prefs)::load,
                primePassword = chat::primePassword,
                connect = chat::connect,
            )
            restorer.restore(
                stayConnected = prefs.stayConnected.first(),
                sessionWanted = prefs.chatSessionWanted.first(),
                savedNick = prefs.chatNick.first(),
                connection = chat.connection.value,
                nick = chat.nick.value,
            )
        }

        // A terminal server rejection is not a reconnectable session. Clear the persisted intent
        // even when no Chat screen/view model exists (for example during a service-only restart).
        appScope.launch {
            chat.nick.collect { state ->
                if (state is NickState.Rejected) prefs.setChatSessionWanted(false)
            }
        }

        // One place decides whether the background service — and its permanent notification — runs.
        // It follows the actual session, not just the preference: with "stay connected" on but no one
        // joined, there is nothing to keep alive, and a notification claiming otherwise is the bug
        // this replaced. The service self-arrives again the moment the user rejoins.
        appScope.launch {
            combine(
                prefs.stayConnected,
                chat.nick,
                chat.transferHold,
                prefs.chatSessionWanted,
            ) { stay, nick, hold, wanted ->
                shouldRunConnectionService(stay, nick, hold, sessionWanted = wanted)
            }
                .distinctUntilChanged()
                .collect { run ->
                    if (run) {
                        ChatConnectionService.start(this@YumiApp).onFailure {
                            chat.showNotice("Android could not start background chat protection.")
                        }
                    } else {
                        ChatConnectionService.stop(this@YumiApp)
                    }
                }
        }
    }
}
