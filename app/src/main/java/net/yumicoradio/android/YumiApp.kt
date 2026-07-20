package net.yumicoradio.android

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.yumicoradio.android.chat.ChatConnectionService
import net.yumicoradio.android.chat.ChatRepository
import net.yumicoradio.android.data.PrefsStore
import net.yumicoradio.android.metadata.*

class YumiApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    val http by lazy { OkHttpClient() }
    lateinit var prefs: PrefsStore; private set
    lateinit var metadata: MetadataRepository; private set
    lateinit var chat: ChatRepository; private set

    override fun onCreate() {
        super.onCreate()
        prefs = PrefsStore(this)
        metadata = MetadataRepository(
            api = AzuraNowPlayingApi(http),
            scope = appScope,
        )
        // Application-scoped so navigating between screens does not disconnect — a screen-scoped
        // connection would broadcast a join/quit pair to every user on every visit.
        chat = ChatRepository(scope = appScope)

        // Bring the background connection back after a reboot or a process restart, so the setting
        // means what it says rather than lasting only until the app is swiped away.
        appScope.launch {
            if (prefs.stayConnected.first()) ChatConnectionService.start(this@YumiApp)
        }
    }
}
