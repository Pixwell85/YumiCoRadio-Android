package net.yumicoradio.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.yumicoradio.android.chat.NotificationMode
import net.yumicoradio.android.playback.StreamQuality

private val Context.dataStore by preferencesDataStore(name = "yumi_prefs")

class PrefsStore(private val context: Context) {
    private val qualityKey = stringPreferencesKey("stream_quality")
    private val volumeKey = floatPreferencesKey("volume")
    private val nickKey = stringPreferencesKey("chat_nick")
    private val notifyKey = stringPreferencesKey("chat_notify_mode")
    private val stayConnectedKey = booleanPreferencesKey("chat_stay_connected")
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    val quality: Flow<StreamQuality> =
        context.dataStore.data.map { StreamQuality.fromId(it[qualityKey]) }

    suspend fun setQuality(q: StreamQuality) {
        context.dataStore.edit { it[qualityKey] = q.id }
    }

    /** 0.0–1.0; defaults to full volume the first time. */
    val volume: Flow<Float> =
        context.dataStore.data.map { (it[volumeKey] ?: 1f).coerceIn(0f, 1f) }

    suspend fun setVolume(v: Float) {
        context.dataStore.edit { it[volumeKey] = v.coerceIn(0f, 1f) }
    }

    /**
     * The chat nickname, blank until the user picks one.
     *
     * Only the nickname is stored. The password for a reserved nickname is deliberately never
     * persisted — it is asked for once per launch, as the website does.
     */
    val chatNick: Flow<String> =
        context.dataStore.data.map { it[nickKey].orEmpty() }

    suspend fun setChatNick(nick: String) {
        context.dataStore.edit { it[nickKey] = nick }
    }

    /** Defaults to mentions and PMs: every message is a lot, and nothing is a surprise. */
    val notificationMode: Flow<NotificationMode> =
        context.dataStore.data.map { NotificationMode.fromId(it[notifyKey]) }

    suspend fun setNotificationMode(mode: NotificationMode) {
        context.dataStore.edit { it[notifyKey] = mode.id }
    }

    /**
     * Whether to hold the chat connection while the app is in the background.
     *
     * Off by default: it costs a permanent notification and battery, so it is the user's call to
     * opt in rather than something sprung on them.
     */
    val stayConnected: Flow<Boolean> =
        context.dataStore.data.map { it[stayConnectedKey] ?: false }

    suspend fun setStayConnected(enabled: Boolean) {
        context.dataStore.edit { it[stayConnectedKey] = enabled }
    }

    /**
     * "Star OS 99 Dark" instead of "Star OS 99".
     *
     * Off by default, and deliberately not seeded from the system setting: on the website dark is a
     * theme you choose from a list, so a phone in dark mode should not silently repaint an app whose
     * whole point is looking like the site.
     */
    val darkMode: Flow<Boolean> =
        context.dataStore.data.map { it[darkModeKey] ?: false }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[darkModeKey] = enabled }
    }
}
