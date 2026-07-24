// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.chat.ChatConnectionService
import net.yumicoradio.android.chat.ChatStatus
import net.yumicoradio.android.chat.PresenceRule
import net.yumicoradio.android.chat.NotificationMode
import net.yumicoradio.android.chat.UploadClient
import net.yumicoradio.android.chat.model.ChatChannel

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
    val quota = repo.quota
    val uploadsEnabled = repo.uploadsEnabled
    val status = repo.status

    // The view model owns the clock, so the auto-away rule lives here rather than in the repository.
    private var presence = PresenceRule()
    private var awayTicker: Job? = null

    /**
     * Announces a status the user picked. A deliberate away sticks: typing afterwards will not quietly
     * flip them back to online, unlike an away that idleness set.
     */
    fun setStatus(status: ChatStatus) {
        val now = System.currentTimeMillis()
        val t = presence.onChosen(status, now)
        presence = t.rule
        if (t.notify) repo.setStatus(status)
        armAwayTicker()
    }

    /** Called on anything the user does in the chat; brings an idle away back to online. */
    private fun onUserActivity() {
        val t = presence.onActivity(System.currentTimeMillis())
        presence = t.rule
        if (t.notify) repo.setStatus(t.rule.status)
        armAwayTicker()
    }

    /**
     * A single pending wake-up at the idle deadline, rescheduled on every activity. One coroutine
     * that sleeps, rather than a timer that fires every second — the only moment that matters is
     * when the deadline passes.
     */
    private fun armAwayTicker() {
        awayTicker?.cancel()
        if (presence.status != ChatStatus.ONLINE) return
        awayTicker = viewModelScope.launch {
            delay(PresenceRule.IDLE_MILLIS)
            val t = presence.onTick(System.currentTimeMillis())
            if (t.notify) {
                presence = t.rule
                repo.setStatus(t.rule.status)
            }
        }
    }

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
    fun holdForTransfer() {
        if (stayConnected.value || transferHeld) return
        transferHeld = true
        ChatConnectionService.start(getApplication())
    }

    /** Undoes [holdForTransfer]. Safe to call when no hold is in place. */
    fun releaseTransferHold() {
        if (!transferHeld) return
        transferHeld = false
        // The preference may have been switched on mid-transfer; then the service is not ours to stop.
        if (!stayConnected.value) ChatConnectionService.stop(getApplication())
    }

    private var transferHeld = false

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
                is UploadClient.Result.Success ->
                    if (toPm != null) repo.sendPm(toPm, result.url) else repo.send(result.url)

                is UploadClient.Result.Failure -> repo.showNotice(result.message)
            }
            _uploading.value = false
            _uploadProgress.value = null
            releaseTransferHold()
        }
    }

    val notificationMode: StateFlow<NotificationMode> =
        yumi.prefs.notificationMode.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationMode.DEFAULT)

    val stayConnected: StateFlow<Boolean> =
        yumi.prefs.stayConnected.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setNotificationMode(mode: NotificationMode) {
        viewModelScope.launch { yumi.prefs.setNotificationMode(mode) }
    }

    /**
     * Starts or stops the background service straight away, rather than waiting for the next launch
     * — a setting that only takes effect later is a setting people think is broken.
     */
    fun setStayConnected(enabled: Boolean) {
        viewModelScope.launch { yumi.prefs.setStayConnected(enabled) }
        val context = getApplication<Application>()
        if (enabled) ChatConnectionService.start(context) else ChatConnectionService.stop(context)
    }

    val storedNick: StateFlow<String> =
        yumi.prefs.chatNick.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun join(nickname: String) {
        viewModelScope.launch { yumi.prefs.setChatNick(nickname) }
        repo.connect(nickname)
    }

    fun submitPassword(nickname: String, password: String) = repo.submitPassword(nickname, password)
    fun submitReservePassword(password: String) = repo.submitReservePassword(password)
    fun cancelReservePassword(previousNick: String) = repo.cancelReservePassword(previousNick)
    fun send(text: String) { onUserActivity(); repo.send(text) }
    fun switchChannel(channel: ChatChannel) = repo.switchChannel(channel)
    fun sendPm(to: String, text: String) { onUserActivity(); repo.sendPm(to, text) }
    fun openPm(nick: String) = repo.openPm(nick)
    fun closePm() = repo.closePm()
    fun dismissPm(nick: String) = repo.dismissPm(nick)
    fun cancelNickPrompt() = repo.cancelNickPrompt()
    fun clearNotice() = repo.clearNotice()
    fun leave() = repo.disconnect()
}
