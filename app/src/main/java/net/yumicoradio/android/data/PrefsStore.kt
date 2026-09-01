// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.yumicoradio.android.chat.ChatFontSize
import net.yumicoradio.android.playback.EqualizerSpec
import net.yumicoradio.android.chat.NotificationMode
import net.yumicoradio.android.playback.StreamQuality

private val Context.dataStore by preferencesDataStore(name = "yumi_prefs")

class PrefsStore(private val context: Context) {
    private val qualityKey = stringPreferencesKey("stream_quality")
    private val volumeKey = floatPreferencesKey("volume")
    private val nickKey = stringPreferencesKey("chat_nick")
    private val notifyKey = stringPreferencesKey("chat_notify_mode")
    private val stayConnectedKey = booleanPreferencesKey("chat_stay_connected")
    private val chatSessionWantedKey = booleanPreferencesKey("chat_session_wanted")
    private val maximumReliabilityKey = booleanPreferencesKey("chat_maximum_reliability")
    private val chatFontSizeKey = stringPreferencesKey("chat_font_size")
    private val chatTimestampsKey = booleanPreferencesKey("chat_show_timestamps")
    private val chatSeparatePresenceKey = booleanPreferencesKey("chat_separate_presence")
    private val chatNickColorKey = stringPreferencesKey("chat_nick_color")
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val eqEnabledKey = booleanPreferencesKey("eq_enabled")
    private val eqGainsKey = stringPreferencesKey("eq_gains")
    private val chatRememberPasswordKey = booleanPreferencesKey("chat_remember_password")
    private val reservedNickKey = stringPreferencesKey("chat_reserved_nick")
    private val reservedPasswordKey = stringPreferencesKey("chat_reserved_pw")
    private val batteryPromptDismissedKey = booleanPreferencesKey("chat_battery_prompt_dismissed")
    private val automaticUpdateChecksKey = booleanPreferencesKey("automatic_fdroid_update_checks")
    private val lastUpdateAttemptKey = longPreferencesKey("last_fdroid_update_attempt")
    private val dismissedUpdateCodeKey = intPreferencesKey("dismissed_fdroid_update_code")
    private val accountTokenKey = stringPreferencesKey("account_session_token")
    private val ratingsVoterTokenKey = stringPreferencesKey("ratings_voter_token")

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
     * On by default: the chat is a live room, and a message that only arrives when the app is
     * open is a message missed. It costs a permanent notification and some battery, and a user who
     * does not want that can turn it off — an explicit `false` is honoured, only the never-touched
     * default changed.
     */
    val stayConnected: Flow<Boolean> =
        context.dataStore.data.map { it[stayConnectedKey] ?: true }

    suspend fun setStayConnected(enabled: Boolean) {
        context.dataStore.edit { it[stayConnectedKey] = enabled }
    }

    /**
     * Whether the user has an active chat session that should survive process recreation.
     *
     * This is deliberately separate from [chatNick]: remembering the last nickname is convenient,
     * but it must not undo an explicit Disconnect the next time Android starts the process.
     */
    val chatSessionWanted: Flow<Boolean> =
        context.dataStore.data.map { it[chatSessionWantedKey] ?: false }

    suspend fun setChatSessionWanted(wanted: Boolean) {
        context.dataStore.edit { it[chatSessionWantedKey] = wanted }
    }

    /** Optional CPU wake lock for devices that freeze foreground sockets with the screen off. */
    val maximumReliability: Flow<Boolean> =
        context.dataStore.data.map { it[maximumReliabilityKey] ?: false }

    suspend fun setMaximumReliability(enabled: Boolean) {
        context.dataStore.edit { it[maximumReliabilityKey] = enabled }
    }

    /**
     * Whether the one-time "keep the chat alive in the background" battery guidance has been shown.
     * Set once the dialog auto-appears so it never nags; the Chat Options row still opens it on
     * demand regardless of this flag.
     */
    val batteryPromptDismissed: Flow<Boolean> =
        context.dataStore.data.map { it[batteryPromptDismissedKey] ?: false }

    suspend fun setBatteryPromptDismissed(value: Boolean) {
        context.dataStore.edit { it[batteryPromptDismissedKey] = value }
    }

    /** Explicit opt-in: false until the user enables contact with F-Droid for update checks. */
    val automaticUpdateChecks: Flow<Boolean> =
        context.dataStore.data.map { it[automaticUpdateChecksKey] ?: false }

    suspend fun setAutomaticUpdateChecks(enabled: Boolean) {
        context.dataStore.edit { it[automaticUpdateChecksKey] = enabled }
    }

    suspend fun lastUpdateAttempt(): Long =
        context.dataStore.data.first()[lastUpdateAttemptKey] ?: 0L

    suspend fun setLastUpdateAttempt(timestamp: Long) {
        context.dataStore.edit { it[lastUpdateAttemptKey] = timestamp }
    }

    suspend fun dismissedUpdateCode(): Int =
        context.dataStore.data.first()[dismissedUpdateCodeKey] ?: 0

    suspend fun setDismissedUpdateCode(versionCode: Int) {
        context.dataStore.edit { it[dismissedUpdateCodeKey] = versionCode }
    }

    /** How large chat text is drawn. Defaults to Normal. */
    val chatFontSize: Flow<ChatFontSize> =
        context.dataStore.data.map { ChatFontSize.fromId(it[chatFontSizeKey]) }

    suspend fun setChatFontSize(size: ChatFontSize) {
        context.dataStore.edit { it[chatFontSizeKey] = size.id }
    }

    /** `[HH:mm]` before each line, chat and PM, as the website shows. On by default, like the site. */
    val chatShowTimestamps: Flow<Boolean> =
        context.dataStore.data.map { it[chatTimestampsKey] ?: true }

    suspend fun setChatShowTimestamps(enabled: Boolean) {
        context.dataStore.edit { it[chatTimestampsKey] = enabled }
    }

    /** True keeps join/leave notices in Activity; false restores the classic public-channel view. */
    val chatSeparatePresence: Flow<Boolean> =
        context.dataStore.data.map { it[chatSeparatePresenceKey] ?: false }

    suspend fun setChatSeparatePresence(enabled: Boolean) {
        context.dataStore.edit { it[chatSeparatePresenceKey] = enabled }
    }

    /** The user's own nickname-colour override: `#rrggbb`, or `""` for the hash-derived default. */
    val chatNickColor: Flow<String> =
        context.dataStore.data.map { it[chatNickColorKey].orEmpty() }

    suspend fun setChatNickColor(color: String) {
        context.dataStore.edit { it[chatNickColorKey] = color }
    }

    val chatRememberPassword: Flow<Boolean> =
        context.dataStore.data.map { it[chatRememberPasswordKey] ?: false }

    suspend fun setChatRememberPassword(enabled: Boolean) {
        context.dataStore.edit { it[chatRememberPasswordKey] = enabled }
    }

    /** The stored reserved nick and its encrypted password blob, or null if none. */
    suspend fun reservedPassword(): Pair<String, String>? {
        val prefs = context.dataStore.data.first()
        val nick = prefs[reservedNickKey]
        val blob = prefs[reservedPasswordKey]
        return if (nick != null && blob != null) nick to blob else null
    }

    suspend fun setReservedPassword(nick: String, blob: String) {
        context.dataStore.edit { it[reservedNickKey] = nick; it[reservedPasswordKey] = blob }
    }

    suspend fun clearReservedPassword() {
        context.dataStore.edit { it.remove(reservedNickKey); it.remove(reservedPasswordKey) }
    }

    /** Encrypted blobs only. Their plaintext values never enter DataStore. */
    suspend fun accountTokenBlob(): String? = context.dataStore.data.first()[accountTokenKey]

    suspend fun setAccountTokenBlob(blob: String) {
        context.dataStore.edit { it[accountTokenKey] = blob }
    }

    suspend fun clearAccountTokenBlob() {
        context.dataStore.edit { it.remove(accountTokenKey) }
    }

    suspend fun ratingsVoterTokenBlob(): String? = context.dataStore.data.first()[ratingsVoterTokenKey]

    suspend fun setRatingsVoterTokenBlob(blob: String) {
        context.dataStore.edit { it[ratingsVoterTokenKey] = blob }
    }

    suspend fun clearRatingsVoterTokenBlob() {
        context.dataStore.edit { it.remove(ratingsVoterTokenKey) }
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

    /** Whether the graphic equaliser is switched on. Off by default — the site starts flat too. */
    val eqEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[eqEnabledKey] ?: false }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[eqEnabledKey] = enabled }
    }

    /** The ten band gains, stored as a comma-separated string. Malformed or wrong-length → flat. */
    val eqGains: Flow<List<Int>> =
        context.dataStore.data.map { prefs ->
            val parsed = prefs[eqGainsKey]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.map { it.coerceIn(EqualizerSpec.MIN_DB, EqualizerSpec.MAX_DB) }
            if (parsed != null && parsed.size == EqualizerSpec.BAND_COUNT) parsed else EqualizerSpec.ZERO_GAINS
        }

    suspend fun setEqGains(gains: List<Int>) {
        context.dataStore.edit { it[eqGainsKey] = gains.joinToString(",") }
    }
}
