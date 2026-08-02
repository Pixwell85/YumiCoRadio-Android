// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ChatMessage
import net.yumicoradio.android.chat.model.ChatUser
import net.yumicoradio.android.chat.model.ConnectionState
import net.yumicoradio.android.chat.model.NickState
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Owns the chat connection.
 *
 * Constructed at application scope, not per screen: a screen-scoped connection would emit a
 * join/quit pair into everyone's chat — and into the host's Telegram bridge — every time the user
 * glanced at the player.
 *
 * Socket.IO invokes its listeners on its own threads, so every handler hops onto [scope] before
 * touching a flow.
 */
class ChatRepository(
    private val scope: CoroutineScope,
    private val serverUrl: String = DEFAULT_URL,
) {
    private val _connection = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val _users = MutableStateFlow<List<ChatUser>>(emptyList())
    val users: StateFlow<List<ChatUser>> = _users.asStateFlow()

    private val _status = MutableStateFlow(ChatStatus.ONLINE)
    val status: StateFlow<ChatStatus> = _status.asStateFlow()

    // Auto-away lives here, not in the view model, because the connection outlives the screen: with
    // "stay connected" on, the socket is held by a foreground service while the activity — and its
    // view-model scope — can be stopped or destroyed. A clock in the view model simply stops ticking
    // when the phone is pocketed, which is exactly when going "away" matters. This scope is the
    // application's, so it runs for as long as the connection does. See [PresenceController] for why
    // it is a steady heartbeat rather than a re-armed timer.
    private val presence = PresenceController(
        scope = scope,
        now = { android.os.SystemClock.elapsedRealtime() },
        onStatus = ::pushStatus,
    )

    /** Mirror the status locally and tell the server. The presence rule decides *when* to call it. */
    private fun pushStatus(status: ChatStatus) {
        _status.value = status
        socket?.emit("set-status", org.json.JSONObject().put("status", status.wire))
    }

    /** The user picked a status from the menu. A deliberate away sticks; see [PresenceRule]. */
    fun setStatus(status: ChatStatus) = presence.choose(status)

    /**
     * A concrete user action in the chat — typing, sending, a button. Clears an automatic away and
     * restarts the idle clock. Ignored unless joined, so actions from before connecting (or from
     * another screen, which never calls this) leave the clock alone.
     */
    fun userActivity() {
        if (_nick.value is NickState.Joined) presence.markActivity()
    }

    private val _nick = MutableStateFlow<NickState>(NickState.Idle)
    val nick: StateFlow<NickState> = _nick.asStateFlow()

    private val _motd = MutableStateFlow<Map<ChatChannel, List<ChatMessage>>>(emptyMap())
    val motd: StateFlow<Map<ChatChannel, List<ChatMessage>>> = _motd.asStateFlow()

    private val _colors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Colours users picked for themselves; everyone else falls back to [NickColors.forNick]. */
    val colors: StateFlow<Map<String, String>> = _colors.asStateFlow()

    private val _pm = MutableStateFlow(PmState())
    val pm: StateFlow<PmState> = _pm.asStateFlow()

    // A one-shot "ping" per incoming PM. No replay and drop-on-overflow, so a PM that arrives while
    // nothing is collecting (app backgrounded) is not queued to sound when the UI returns.
    private val _pmSound = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pmSound: SharedFlow<Unit> = _pmSound.asSharedFlow()

    // A file pick sends our process to the background, where the OS may freeze the socket. This flag
    // keeps the foreground service alive across the pick even when "stay connected" is off. It lives
    // here, at application scope, so the single service gate can weigh it against the preference — a
    // flag held in the view model would be torn down with the screen mid-transfer.
    private val _transferHold = MutableStateFlow(false)
    val transferHold: StateFlow<Boolean> = _transferHold.asStateFlow()

    fun holdForTransfer() { _transferHold.value = true }
    fun releaseTransferHold() { _transferHold.value = false }

    private val _uploadToken = MutableStateFlow<String?>(null)

    /** The server's CSRF token for uploads, reissued over the socket. */
    val uploadToken: StateFlow<String?> = _uploadToken.asStateFlow()

    private val _quota = MutableStateFlow(UploadQuota())
    val quota: StateFlow<UploadQuota> = _quota.asStateFlow()

    private val _uploadsEnabled = MutableStateFlow(true)

    /** Uploads can be switched off server-side; the button reflects that rather than failing late. */
    val uploadsEnabled: StateFlow<Boolean> = _uploadsEnabled.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** One-shot server warnings and moderation notices, for the screen to surface and clear. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var socket: Socket? = null

    /**
     * A reserved nick's password, kept in memory for as long as the user stays connected.
     *
     * It has to outlive the first handshake. socket.io reconnects by itself after any drop and
     * `EVENT_CONNECT` replays the join — with no password, the server refuses a reserved nick
     * and the user is thrown out without touching anything. That is what made
     * uploads fail: opening the file picker backgrounds the app long enough for the socket to die.
     *
     * In memory only. It is never written to disk, and [disconnect] and [cancelNickPrompt] drop it
     * — leaving the chat means the next join asks again, exactly as the website does.
     */
    // @Volatile: written from the Main-scope callers (connect/submitPassword) but read on socket.io's
    // own EventThread inside the EVENT_CONNECT reconnect replay. Without the barrier that replay could
    // miss a just-set password — the exact race the reconnect logic exists to close.
    @Volatile
    private var sessionPassword: String? = null

    /**
     * The user's own nickname colour, replayed in the auto-reconnect [join] just like [currentNick].
     *
     * @Volatile for the same reason as [sessionPassword]: written from the Main-scope caller, read on
     * socket.io's EventThread during the EVENT_CONNECT replay.
     */
    @Volatile
    private var nickColor: String = ""

    /**
     * The nickname the client currently holds. Mutable because a reservation reset joins under a
     * new one (`Bob_` → `Bob`): the `wire` closures capture the *initial* nick, so without this the
     * `joined` handler would report the old name and the auto-reconnect would replay the old join —
     * dropping the user off the slot they were just given. [join] is the single writer.
     *
     * @Volatile for the same reason as [sessionPassword]: the EVENT_CONNECT replay reads it on the
     * EventThread, while [join] writes it from the Main scope.
     */
    @Volatile
    private var currentNick: String? = null

    fun connect(nickname: String) {
        if (socket != null) {
            // Already wired up; just (re)issue the join.
            join(nickname)
            return
        }
        _connection.value = ConnectionState.CONNECTING
        // Default transports on purpose: polling first, upgrading to websocket — the same thing the
        // web client does. Forcing websocket-only has no fallback, so anywhere the upgrade is
        // blocked (a mobile carrier proxy, a captive portal) the client hangs on CONNECTING with
        // nothing on screen to say why.
        val s = IO.socket(URI.create(serverUrl))
        socket = s
        wire(s, nickname)
        s.connect()
    }

    private fun wire(s: Socket, nickname: String) {
        s.on(Socket.EVENT_CONNECT) {
            on { _connection.value = ConnectionState.CONNECTED }
            // Replay the join under whatever nick we hold now, not the one wire() was opened with —
            // a reservation reset may have moved us since.
            join(currentNick ?: nickname)
        }
        s.on(Socket.EVENT_DISCONNECT) {
            on { _connection.value = ConnectionState.DISCONNECTED }
        }
        // Without this the client sits on CONNECTING for ever with nothing on screen explaining it.
        // That is exactly how a wrong server URL presented itself: silence.
        s.on(Socket.EVENT_CONNECT_ERROR) { args ->
            val detail = args.firstOrNull()?.toString().orEmpty().take(200)
            on {
                _connection.value = ConnectionState.DISCONNECTED
                // The notice explains the failure; a nickname prompt on top of it would only be
                // in the way, since the nickname was never the problem.
                _nick.value = NickState.Idle
                _notice.value = "Could not reach the chat server$SERVER_HINT" +
                    if (detail.isNotEmpty()) "\n\n$detail" else ""
            }
        }
        s.on("joined") {
            on {
                _nick.value = NickState.Joined(currentNick ?: nickname)
                // First join starts the clock; a reconnect replay leaves it alone and re-asserts our
                // held status. All of that reasoning now lives in the controller.
                presence.onJoined()
            }
        }
        s.on("message") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            val msg = ChatProtocol.parseMessage(json) ?: return@on
            on { _state.update { it.received(msg) } }
        }
        s.on("user-list") { args ->
            val arr = args.firstOrNull() as? JSONArray ?: return@on
            val list = ChatProtocol.parseUserList(arr)
            on {
                _users.value = list
                // The list carries each user's own colour pick; keep the map in step with it.
                _colors.update { it + list.mapNotNull { u -> u.color?.let { u.nickname to it } } }
            }
        }
        s.on("nick-color") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            val who = json.optString("nickname").takeIf { it.isNotEmpty() } ?: return@on
            val color = json.optString("color")
            on {
                _colors.update { if (color.isEmpty()) it - who else it + (who to color) }
            }
        }
        s.on("motd-all") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            val motd = ChatProtocol.parseMotd(json)
            on {
                _motd.value = motd
                // Printed into each channel's buffer the way the site prints it on join — a MOTD
                // nobody can see is the same as no MOTD.
                motd.values.flatten().forEach { line ->
                    _state.update { it.received(line) }
                }
            }
        }
        s.on("private-message") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            val from = json.optString("from").takeIf { it.isNotEmpty() } ?: return@on
            val text = json.optString("text").takeIf { it.isNotEmpty() } ?: return@on
            val type = json.optString("type").ifEmpty { "user" }
            on {
                val active = _state.value.active
                _pm.update { it.received(from, ChatMessage(from, text, type, active)) }
                _pmSound.tryEmit(Unit)
            }
        }
        s.on("upload-token") { args ->
            val token = (args.firstOrNull() as? JSONObject)?.optString("token").orEmpty()
            if (token.isNotEmpty()) on { _uploadToken.value = token }
        }
        s.on("upload-quota") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            on {
                _quota.value = UploadQuota(
                    used = json.optLong("used"),
                    limit = json.optLong("limit", UploadQuota.DEFAULT_LIMIT),
                    resetAt = json.optLong("resetAt"),
                )
            }
        }
        s.on("uploads-status") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            on { _uploadsEnabled.value = json.optBoolean("enabled", true) }
        }
        s.on("nick-rejected") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            val reason = json.optString("reason")
            val rejected = json.optString("nickname").ifEmpty { nickname }
            on {
                _nick.value =
                    if (reason == "reserved") NickState.NeedsPassword(rejected)
                    else NickState.Rejected(rejected, reason)
            }
        }
        // An admin has begun reserving a nickname for this user. The password is chosen here and
        // sent back on this same socket — nothing is written on the server until it arrives, so no
        // reserved-but-unlocked nickname ever exists.
        s.on("reserve-prompt") { args ->
            val slot = (args.firstOrNull() as? JSONObject)?.optString("slot")?.takeIf { it.isNotEmpty() }
                ?: return@on
            on {
                // The user is chatting when this arrives; remember under what name, so cancelling
                // the prompt returns them there instead of dropping them.
                val here = (_nick.value as? NickState.Joined)?.nickname ?: nickname
                _nick.value = NickState.SettingPassword(slot, here)
            }
        }
        // The server rejected the chosen password (length). Keep the dialog up with the reason
        // rather than dropping it, the way the website does.
        s.on("reserve-error") { args ->
            val reason = (args.firstOrNull() as? JSONObject)?.optString("reason").orEmpty()
            on {
                (_nick.value as? NickState.SettingPassword)?.let { current ->
                    _nick.value = current.copy(
                        error = if (reason == "length") {
                            "At least ${ReservePassword.MIN_LENGTH} characters."
                        } else {
                            "The server refused that password."
                        },
                    )
                }
            }
        }
        // The reservation took. Re-join under the reserved nickname, carrying the password just
        // set — the same-socket rejoin frees the old nick, exactly as the website relies on.
        s.on("reserve-done") { args ->
            val slot = (args.firstOrNull() as? JSONObject)?.optString("slot")?.takeIf { it.isNotEmpty() }
                ?: return@on
            on { join(slot) }
        }
        s.on("warning") { args ->
            val text = (args.firstOrNull() as? JSONObject)?.optString("text").orEmpty()
            if (text.isNotEmpty()) on { _notice.value = text }
        }
        s.on("moderation") { args ->
            val json = args.firstOrNull() as? JSONObject ?: return@on
            val action = json.optString("action")
            val reason = json.optString("reason").ifEmpty { "no reason given" }
            on { _notice.value = "You were ${action}ed: $reason" }
        }
    }

    /** Re-issues the join, carrying [sessionPassword] when a reserved nick needs one. */
    private fun join(nickname: String) {
        currentNick = nickname   // single source of truth for the auto-reconnect replay
        _nick.value = NickState.Joining(nickname)
        socket?.emit("join", ChatProtocol.joinPayload(nickname, sessionPassword, nickColor))
    }

    /**
     * Records the user's own colour and applies it everywhere at once.
     *
     * The colour is remembered for the reconnect replay, pushed to the server as a bare string (the
     * exact shape `socket.on('nick-color', color => …)` expects) when we are connected, and merged
     * into [_colors] under our own nick so our messages and roster entry recolour immediately rather
     * than only after the server echoes the change back. `""` means "Auto" and clears the override.
     */
    fun setNickColor(color: String) {
        // DataStore re-emits its whole snapshot on every unrelated write (a volume drag, a join),
        // so the prefs collector calls this with an unchanged value constantly. Without this guard
        // each of those would emit `nick-color` and the server would rebroadcast it to the room.
        if (color == nickColor) return
        nickColor = color
        if (_connection.value == ConnectionState.CONNECTED) socket?.emit("nick-color", color)
        val me = currentNick ?: return
        _colors.update { if (color.isBlank()) it - me else it + (me to color) }
    }

    /**
     * Seeds the in-memory session password before the first join, so a remembered reserved nick is
     * admitted without a prompt. [connect] does not touch [sessionPassword], and the join replayed on
     * EVENT_CONNECT carries it — exactly as a manual submit would.
     */
    fun primePassword(password: String?) {
        if (password != null) sessionPassword = password
    }

    /** The password held for the current session (null when none), so it can be remembered later. */
    val currentPassword: String? get() = sessionPassword

    /** Answers a [NickState.NeedsPassword] prompt. The password is kept for the session. */
    fun submitPassword(nickname: String, password: String) {
        sessionPassword = password
        join(nickname)
    }

    /**
     * Sets the password an admin's reservation prompt asked for. Kept for the session so the
     * `reserve-done` rejoin carries it, then sent to the server, which writes the entry only now.
     */
    fun submitReservePassword(password: String) {
        sessionPassword = password
        socket?.emit("reserve-password", JSONObject().put("password", password))
    }

    /** Backs out of a reservation prompt without setting a password; stays connected as before. */
    fun cancelReservePassword(previousNick: String) {
        _nick.value = NickState.Joined(previousNick)
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        presence.markActivity()
        socket?.emit("send-message", ChatProtocol.messagePayload(trimmed))
    }

    /**
     * Sends a private message and records it locally — the server does not echo PMs back to their
     * sender, so without this the thread would show only the other side.
     */
    fun sendPm(to: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val me = (_nick.value as? NickState.Joined)?.nickname ?: return
        presence.markActivity()
        socket?.emit(
            "private-message",
            JSONObject().put("to", to).put("text", trimmed).put("type", "user"),
        )
        val active = _state.value.active
        _pm.update { it.sent(to, ChatMessage(me, trimmed, "user", active)) }
    }

    fun openPm(nick: String) { _pm.update { it.opened(nick) } }

    fun closePm() { _pm.update { it.closed() } }

    fun hidePm(nick: String) { _pm.update { it.hidden(nick) } }

    /** The server derives the sending channel from its own state, so this must round-trip. */
    fun switchChannel(channel: ChatChannel) {
        socket?.emit("join-channel", ChatProtocol.channelPayload(channel))
        _state.update { it.switchedTo(channel) }
    }

    /** Wipes the active channel's on-screen buffer, as the website's Clear button does. Local only. */
    fun clearActive() {
        _state.update { it.cleared(it.active) }
    }

    /**
     * Asks the server for the current upload usage. The server pushes `upload-quota` on join but not
     * after an upload — the HTTP upload endpoint never touches this socket — so the quota window and
     * the post-upload path re-request it to stay live, exactly as the website does.
     */
    fun refreshQuota() {
        socket?.emit("get-quota")
    }

    /**
     * Backs out of a nickname or password prompt without joining.
     *
     * There must always be a way out of a modal dialog: while one is up the whole app is blocked,
     * so a prompt with no exit is a trap.
     */
    fun cancelNickPrompt() {
        sessionPassword = null
        _nick.value = NickState.Idle
    }

    /** Lets the view model surface its own failures through the same channel as the server's. */
    fun showNotice(text: String) {
        _notice.value = text
    }

    fun clearNotice() {
        _notice.value = null
    }

    fun disconnect() {
        presence.stop()
        socket?.off()
        socket?.disconnect()
        socket?.close()
        socket = null
        sessionPassword = null
        currentNick = null
        _connection.value = ConnectionState.DISCONNECTED
        _users.value = emptyList()
        // Idle, not NeedsNick: the user asked to leave, so this is no moment to demand a nickname.
        _nick.value = NickState.Idle
        // Said in the chat itself, not just in the toolbar: leaving should leave a mark where you
        // are looking. Buffers survive on purpose — reconnecting should not wipe what was said.
        systemLine("Disconnected from the server.")
    }

    /** A locally generated notice, shown in every channel like the server's own broadcasts. */
    private fun systemLine(text: String) {
        _state.update { state ->
            state.received(
                ChatMessage(
                    user = "System",
                    text = text,
                    type = "system",
                    channel = state.active,
                    allChannels = true,
                ),
            )
        }
    }

    private inline fun on(crossinline block: () -> Unit) {
        scope.launch { block() }
    }

    companion object {
        /**
         * The chat lives on the stream host, not the website host.
         *
         * `https://yumicoradio.net/socket.io/` is a 404 — pointing there is what left the app stuck
         * on "Connecting…". The site's own CSP names the real one:
         * `connect-src … https://s1.yumicoradio.net wss://s1.yumicoradio.net`, and
         * `js/yumiChat-v2.js` sets `SERVER_URL` to the same value.
         */
        const val DEFAULT_URL = "https://s1.yumicoradio.net"

        private const val SERVER_HINT = " ($DEFAULT_URL). Check your connection and try again."
    }
}
