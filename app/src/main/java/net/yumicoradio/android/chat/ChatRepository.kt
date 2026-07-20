package net.yumicoradio.android.chat

import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    /**
     * Tells the server the caller's presence and remembers it locally. The auto-away rule lives in
     * the view model, which owns the clock; this is only the wire and the mirror.
     */
    fun setStatus(status: ChatStatus) {
        _status.value = status
        socket?.emit("set-status", org.json.JSONObject().put("status", status.wire))
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
    private var sessionPassword: String? = null

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
            join(nickname)
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
                _nick.value = NickState.Joined(nickname)
                // The server starts every join online, so mirror that rather than carrying a stale
                // "away" across a reconnect.
                _status.value = ChatStatus.ONLINE
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
        _nick.value = NickState.Joining(nickname)
        socket?.emit("join", ChatProtocol.joinPayload(nickname, sessionPassword))
    }

    /** Answers a [NickState.NeedsPassword] prompt. The password is kept for the session. */
    fun submitPassword(nickname: String, password: String) {
        sessionPassword = password
        join(nickname)
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
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
        socket?.emit(
            "private-message",
            JSONObject().put("to", to).put("text", trimmed).put("type", "user"),
        )
        val active = _state.value.active
        _pm.update { it.sent(to, ChatMessage(me, trimmed, "user", active)) }
    }

    fun openPm(nick: String) { _pm.update { it.opened(nick) } }

    fun closePm() { _pm.update { it.closed() } }

    fun dismissPm(nick: String) { _pm.update { it.dismissed(nick) } }

    /** The server derives the sending channel from its own state, so this must round-trip. */
    fun switchChannel(channel: ChatChannel) {
        socket?.emit("join-channel", ChatProtocol.channelPayload(channel))
        _state.update { it.switchedTo(channel) }
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
        socket?.off()
        socket?.disconnect()
        socket?.close()
        socket = null
        sessionPassword = null
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
