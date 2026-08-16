// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.yumicoradio.android.chat.ChatConnectionService
import net.yumicoradio.android.chat.ChatRepository
import net.yumicoradio.android.chat.shouldRunConnectionService
import net.yumicoradio.android.data.PrefsStore
import net.yumicoradio.android.metadata.*

class YumiApp : Application() {
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

        // One place decides whether the background service — and its permanent notification — runs.
        // It follows the actual session, not just the preference: with "stay connected" on but no one
        // joined, there is nothing to keep alive, and a notification claiming otherwise is the bug
        // this replaced. The service self-arrives again the moment the user rejoins.
        appScope.launch {
            combine(
                prefs.stayConnected,
                chat.nick,
                chat.transferHold,
            ) { stay, nick, hold -> shouldRunConnectionService(stay, nick, hold) }
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
