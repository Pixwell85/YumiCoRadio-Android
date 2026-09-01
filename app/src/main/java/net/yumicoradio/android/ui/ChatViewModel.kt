// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.chat.ChatFontSize
import net.yumicoradio.android.chat.ChatStatus
import net.yumicoradio.android.chat.NotificationMode
import net.yumicoradio.android.chat.ModerationAction
import net.yumicoradio.android.chat.SecurePasswordStore
import net.yumicoradio.android.chat.UploadClient
import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.NickState

/**
 * Screen-facing wrapper over the application-scoped [net.yumicoradio.android.chat.ChatRepository].
 *
 * The ViewModel is disposable; the connection it talks to is not. That split is what lets the user
 * leave the chat screen without leaving the chat.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val yumi = app as YumiApp
    private val repo = yumi.chat

    val connection = repo.connection
    val state = repo.state
    val users = repo.users
    val nick = repo.nick
    val motd = repo.motd
    val notice = repo.notice
    val colors = repo.colors
    val pm = repo.pm
    val pmSound = repo.pmSound
    val quota = repo.quota
    val uploadsEnabled = repo.uploadsEnabled
    val status = repo.status

    /**
     * Announces a status the user picked. The auto-away clock itself lives in the repository, where
     * it keeps ticking while the app is backgrounded — see [net.yumicoradio.android.chat.ChatRepository].
     */
    fun setStatus(status: ChatStatus) = repo.setStatus(status)

    /** A keystroke or a button on the chat screen — resets the auto-away idle clock. */
    fun onUserActivity() = repo.userActivity()

    private val _uploading = MutableStateFlow(false)

    /** True while a file is on its way, so the UI can say so instead of looking frozen. */
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _uploadProgress = MutableStateFlow<UploadClient.Progress?>(null)

    /** Bytes, percentage and rate while an upload runs; null when nothing is in flight. */
    val uploadProgress: StateFlow<UploadClient.Progress?> = _uploadProgress.asStateFlow()

    private val uploads = UploadClient(yumi.http)

    /**
     * Uploads [uri], then posts its URL as a message — which is what makes it appear for the
     * recipient, exactly as the website does it.
     *
     * [toPm] routes the resulting URL to a private conversation instead of the public channel. The
     * upload endpoint is the same either way; only the message that announces it differs.
     */
    /**
     * Keeps the connection alive across a file pick.
     *
     * The picker is another app, so ours goes to the background and the OS is free to freeze it.
     * The socket then dies quietly, and the user returns from choosing a file to find they have been
     * dropped — the upload fails, and with a reserved nickname the rejoin is refused outright.
     *
     * Picking a file to send *is* using the chat, so the foreground service runs for the length of
     * the transfer whatever "Stay connected in the background" says, and stops again straight after.
     * Callers must pair this with [releaseTransferHold] on every exit, cancelled picks included.
     */
    fun holdForTransfer() = repo.holdForTransfer()

    /** Undoes [holdForTransfer]. Safe to call when no hold is in place. */
    fun releaseTransferHold() = repo.releaseTransferHold()

    fun upload(uri: Uri, toPm: String? = null) {
        viewModelScope.launch {
            _uploading.value = true
            _uploadProgress.value = null
            val result = uploads.upload(
                context = getApplication(),
                uri = uri,
                token = repo.uploadToken.value,
                onProgress = { _uploadProgress.value = it },
            )
            when (result) {
                is UploadClient.Result.Success -> {
                    if (toPm != null) repo.sendPm(toPm, result.url) else repo.send(result.url)
                    // The upload just changed the server-side usage; pull the fresh number so the
                    // quota window and status don't sit stale until the next reconnect.
                    repo.refreshQuota()
                }

                is UploadClient.Result.Failure -> repo.showNotice(result.message)
            }
            _uploading.value = false
            _uploadProgress.value = null
            releaseTransferHold()
        }
    }

    /** A picked file held above the composer, awaiting send — [target] is null for the channel, a
     *  nickname for a PM. Mirrors the website's staging bar rather than firing off the pick at once. */
    data class StagedUpload(
        val uri: Uri,
        val name: String,
        val size: Long,
        val isImage: Boolean,
        val target: String?,
    )

    private val _staged = MutableStateFlow<StagedUpload?>(null)
    val staged: StateFlow<StagedUpload?> = _staged.asStateFlow()

    /**
     * Holds a picked file above the composer instead of sending it immediately. The transfer hold
     * taken to survive the picker is released here: the upload now happens later, in the foreground,
     * on send. Only one file stages at a time — a second pick replaces the first.
     */
    fun stageUpload(uri: Uri, toPm: String? = null) {
        val resolver = getApplication<Application>().contentResolver
        var name = "upload"
        var size = -1L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) c.getString(nameIdx)?.let { name = it }
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) size = c.getLong(sizeIdx)
                }
            }
        }
        val isImage = runCatching { resolver.getType(uri)?.startsWith("image/") == true }.getOrDefault(false)
        _staged.value = StagedUpload(uri, name, size, isImage, toPm)
        releaseTransferHold()
    }

    /** Drops the staged file without sending it. */
    fun clearStaged() {
        _staged.value = null
        releaseTransferHold()
    }

    /**
     * Sends the staged file. A typed message goes first as its own line (as on the website), then
     * the file uploads to the same target. No-op when nothing is staged.
     */
    fun sendStaged(text: String) {
        val s = _staged.value ?: return
        val msg = text.trim()
        if (msg.isNotEmpty()) {
            if (s.target != null) repo.sendPm(s.target, msg) else repo.send(msg)
        }
        _staged.value = null
        upload(s.uri, s.target)
    }

    val notificationMode: StateFlow<NotificationMode> =
        yumi.prefs.notificationMode.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationMode.DEFAULT)

    val stayConnected: StateFlow<Boolean> =
        yumi.prefs.stayConnected.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val maximumReliability: StateFlow<Boolean> =
        yumi.prefs.maximumReliability.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val chatFontSize: StateFlow<ChatFontSize> =
        yumi.prefs.chatFontSize.stateIn(viewModelScope, SharingStarted.Eagerly, ChatFontSize.DEFAULT)

    fun setChatFontSize(size: ChatFontSize) {
        viewModelScope.launch { yumi.prefs.setChatFontSize(size) }
    }

    val showTimestamps: StateFlow<Boolean> =
        yumi.prefs.chatShowTimestamps.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setShowTimestamps(enabled: Boolean) {
        viewModelScope.launch { yumi.prefs.setChatShowTimestamps(enabled) }
    }

    val separatePresenceActivity: StateFlow<Boolean> =
        yumi.prefs.chatSeparatePresence.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setSeparatePresenceActivity(enabled: Boolean) {
        repo.setSeparatePresenceActivity(enabled)
        viewModelScope.launch { yumi.prefs.setChatSeparatePresence(enabled) }
    }

    fun setNotificationMode(mode: NotificationMode) {
        viewModelScope.launch { yumi.prefs.setNotificationMode(mode) }
    }

    /**
     * Persists the preference. The service itself is started and stopped by the app-scope gate in
     * [YumiApp], which weighs this against the live session — turning the setting on with no one
     * joined must not raise a "connected" notification, and turning it off must not tear down a
     * transfer hold. Both are that gate's job, not this setter's.
     */
    fun setStayConnected(enabled: Boolean) {
        viewModelScope.launch { yumi.prefs.setStayConnected(enabled) }
    }

    fun setMaximumReliability(enabled: Boolean) {
        viewModelScope.launch { yumi.prefs.setMaximumReliability(enabled) }
    }

    val batteryPromptDismissed: StateFlow<Boolean> =
        yumi.prefs.batteryPromptDismissed.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Records that the one-time background-reliability guidance has been shown, so it never nags. */
    fun dismissBatteryPrompt() {
        viewModelScope.launch { yumi.prefs.setBatteryPromptDismissed(true) }
    }

    // null means DataStore has not emitted yet; an empty string is a loaded, genuinely absent nick.
    // Collapsing those states opened a blank nickname dialog after process recreation.
    val accountUsername: StateFlow<String?> = yumi.account.state
        .map { if (it.restoring) null else it.username }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val storedNick: StateFlow<String?> = combine(yumi.account.state, yumi.prefs.chatNick) { account, saved ->
        if (account.restoring) null else account.username ?: saved
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val nickColor: StateFlow<String> =
        yumi.prefs.chatNickColor.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val passwordStore = SecurePasswordStore(yumi.prefs)

    // The password used this session (typed or primed) and whether it came from the store. Used to
    // persist on a successful join and to wipe a stale stored password when the server still refuses.
    private var lastPassword: String? = null
    private var primedFromStore: Boolean = false

    // join() now awaits DataStore/Keystore reads before repo.connect() sets CONNECTING. This guard
    // closes that window: without it, the auto-join LaunchedEffect (which fires while connection is
    // still DISCONNECTED) or a toolbar tap could start a second connect and wire a second socket.
    // Main-confined (viewModelScope is Main.immediate; the flag is set before the first suspension).
    private var joinInFlight = false

    val rememberPassword: StateFlow<Boolean> =
        yumi.prefs.chatRememberPassword.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * Persist the pick. The repository is driven from the persisted value by the collector in
     * `init`, so there is one path into `repo.setNickColor` — the picker never calls it directly.
     */
    fun setNickColor(color: String) {
        viewModelScope.launch { yumi.prefs.setChatNickColor(color) }
    }

    init {
        // One source of truth for the colour: whatever is persisted is pushed into the repository,
        // which sends it on join and on change. Runs once with the stored value at startup (before
        // connecting, so it just primes the field), then again on every later pick.
        viewModelScope.launch {
            yumi.prefs.chatNickColor.distinctUntilChanged().collect { repo.setNickColor(it) }
        }

        // Persist a working password once joined; drop a stale stored one when the server refuses it.
        viewModelScope.launch {
            repo.nick.collect { st ->
                when (st) {
                    is NickState.Joined ->
                        if (rememberPassword.value && lastPassword != null) {
                            passwordStore.save(st.nickname, lastPassword!!)
                        }
                    is NickState.NeedsPassword ->
                        if (primedFromStore) {
                            passwordStore.clear(); lastPassword = null; primedFromStore = false
                        }
                    // Reserved rejections surface as NeedsPassword today, but wipe here too so the
                    // stale-password cleanup does not silently depend on that mapping.
                    is NickState.Rejected ->
                        if (primedFromStore && st.reason == "reserved") {
                            passwordStore.clear(); lastPassword = null; primedFromStore = false
                        }
                    else -> Unit
                }
            }
        }
    }

    fun join(nickname: String) {
        if (joinInFlight) return
        joinInFlight = true
        viewModelScope.launch {
            try {
                val effectiveNickname = yumi.account.state.value.username ?: nickname
                yumi.prefs.setChatNick(effectiveNickname)
                yumi.prefs.setChatSessionWanted(true)
                // Fresh join: forget any password from a previous nick so it can't be stored under this one.
                lastPassword = null
                primedFromStore = false
                // .first(), not the StateFlow's value: the very first auto-join can outrun the pref's
                // Eagerly-seeded initial (false) and would then skip priming a remembered password.
                if (yumi.account.state.value.username == null && yumi.prefs.chatRememberPassword.first()) {
                    passwordStore.load(effectiveNickname)?.let {
                        lastPassword = it
                        primedFromStore = true
                        repo.primePassword(it)
                    }
                }
                repo.connect(effectiveNickname)
            } finally {
                joinInFlight = false
            }
        }
    }

    fun submitPassword(nickname: String, password: String, remember: Boolean) {
        lastPassword = password
        primedFromStore = false
        viewModelScope.launch {
            yumi.prefs.setChatRememberPassword(remember)
            if (!remember) passwordStore.clear()
            repo.submitPassword(nickname, password)
        }
    }

    fun submitReservePassword(password: String) {
        lastPassword = password
        primedFromStore = false
        repo.submitReservePassword(password)
    }

    fun setRememberPassword(on: Boolean) {
        viewModelScope.launch {
            yumi.prefs.setChatRememberPassword(on)
            if (on) {
                val joined = (repo.nick.value as? NickState.Joined)?.nickname
                // Fall back to the repo's live session password: after a reconnect lastPassword may be
                // null while the session still holds the password for the nick we are joined as.
                val pw = lastPassword ?: repo.currentPassword
                if (joined != null && pw != null) passwordStore.save(joined, pw)
            } else {
                passwordStore.clear()
            }
        }
    }
    fun cancelReservePassword(previousNick: String) = repo.cancelReservePassword(previousNick)
    fun send(text: String) = repo.send(text)
    fun switchChannel(channel: ChatChannel) = repo.switchChannel(channel)

    fun clearPublicHistory() = repo.clearPublicHistory()
    fun refreshQuota() = repo.refreshQuota()
    fun moderate(target: String, action: ModerationAction) = repo.moderate(target, action)
    fun setUploadsEnabled(enabled: Boolean) = repo.setUploadsEnabled(enabled)
    fun sendPm(to: String, text: String, onResult: (Boolean) -> Unit = {}) =
        repo.sendPm(to, text, onResult)
    fun openPm(nick: String) = repo.openPm(nick)
    fun closePm() = repo.closePm()
    fun hidePm(nick: String) = repo.hidePm(nick)

    // Cached for the session: a LazyColumn re-runs the row's fetch every time it scrolls back into
    // view, so without this an untagged upload would re-issue a 404 on every pass. Nullable values
    // (a "no tags" result) must be cached too, hence a synchronized map rather than ConcurrentHashMap.
    private val audioTagsCache = java.util.Collections.synchronizedMap(HashMap<String, net.yumicoradio.android.chat.AudioTags?>())

    /** Fetches the sidecar tags for an uploaded audio URL; station-only, off the main thread, cached. */
    suspend fun audioTags(url: String): net.yumicoradio.android.chat.AudioTags? {
        synchronized(audioTagsCache) { if (audioTagsCache.containsKey(url)) return audioTagsCache[url] }
        val tags = net.yumicoradio.android.chat.AudioTags.fetch(yumi.http, url)
        synchronized(audioTagsCache) { audioTagsCache[url] = tags }
        return tags
    }
    fun cancelNickPrompt() {
        viewModelScope.launch {
            yumi.prefs.setChatSessionWanted(false)
            repo.cancelNickPrompt()
        }
    }
    fun clearNotice() = repo.clearNotice()
    fun leave() {
        viewModelScope.launch {
            // Persist first: if Android removes the process between these two operations, the next
            // process must still honour the user's explicit Disconnect.
            yumi.prefs.setChatSessionWanted(false)
            repo.disconnect()
        }
    }
}
