package net.yumicoradio.android.chat.model

/**
 * The server's three channels. [slug] is the wire value; the server derives the sending channel
 * from server-side state, so this is only ever used to *ask* to switch and to route what arrives.
 */
enum class ChatChannel(val slug: String, val label: String) {
    GENERAL("general", "#general"),
    MUSIC("music", "#music"),
    SHITPOSTING("shitposting", "#shitposting");

    companion object {
        val DEFAULT = GENERAL
        fun fromSlug(slug: String?): ChatChannel = entries.firstOrNull { it.slug == slug } ?: DEFAULT
    }
}

/**
 * One line in a channel.
 *
 * [allChannels] marks server notices (join, quit, nick change, kick) that belong in every buffer
 * rather than just one.
 */
data class ChatMessage(
    val user: String,
    val text: String,
    val type: String,
    val channel: ChatChannel,
    val allChannels: Boolean = false,
) {
    val isSystem: Boolean get() = type == "system"

    /** MOTD lines are authored by the server, so they render without a nickname. */
    val isMotd: Boolean get() = user == "MOTD"
}

data class ChatUser(val nickname: String, val color: String? = null, val status: String? = null)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

/** Where the join handshake stands. The password step exists because reserved nicks need one. */
sealed interface NickState {
    /**
     * Not joined, and not trying to be — after a disconnect, or before the first connect.
     *
     * Distinct from [NeedsNick] on purpose: disconnecting used to land on NeedsNick, which popped
     * an undismissable nickname dialog at the very moment the user had asked to leave.
     */
    data object Idle : NickState

    /** A join was attempted with no nickname to use — the screen asks for one. */
    data object NeedsNick : NickState
    data class Joining(val nickname: String) : NickState
    /** The server said this nick is reserved; it needs a password before the join will take. */
    data class NeedsPassword(val nickname: String) : NickState
    data class Joined(val nickname: String) : NickState
    data class Rejected(val nickname: String, val reason: String) : NickState
}
