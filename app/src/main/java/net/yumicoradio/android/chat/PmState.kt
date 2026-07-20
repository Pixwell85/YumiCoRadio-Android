package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatMessage

/**
 * Private conversations, one per nickname.
 *
 * [active] is the thread shown over the main window; null means the main window. [open] is the set
 * that keeps a button next to the channels — closing a PM window hides it without losing the
 * thread, so it can be reopened from that button.
 *
 * Immutable and free of Compose or sockets, so the routing and unread rules are testable on their
 * own.
 */
data class PmState(
    val conversations: Map<String, List<ChatMessage>> = emptyMap(),
    val open: Set<String> = emptySet(),
    val unread: Set<String> = emptySet(),
    val active: String? = null,
) {
    fun messages(nick: String): List<ChatMessage> = conversations[nick].orEmpty()

    /** Someone wrote to us: the thread appears, and flags unread unless it is already on screen. */
    fun received(from: String, message: ChatMessage): PmState =
        append(from, message).copy(
            open = open + from,
            unread = if (active == from) unread else unread + from,
        )

    /**
     * Our own outgoing message.
     *
     * The server does not echo PMs back to their sender, so without keeping this copy a
     * conversation would show only the other side.
     */
    fun sent(to: String, message: ChatMessage): PmState =
        append(to, message).copy(open = open + to)

    fun opened(nick: String): PmState =
        copy(active = nick, open = open + nick, unread = unread - nick)

    /** Hides the window; the thread and its button stay. */
    fun closed(): PmState = copy(active = null)

    /** Drops the thread altogether, button included. */
    fun dismissed(nick: String): PmState = copy(
        conversations = conversations - nick,
        open = open - nick,
        unread = unread - nick,
        active = if (active == nick) null else active,
    )

    private fun append(nick: String, message: ChatMessage): PmState =
        copy(conversations = conversations + (nick to (messages(nick) + message).takeLast(MAX_PER_THREAD)))

    companion object {
        /** Same reasoning as the channel buffers: nothing is stored server-side to reload. */
        const val MAX_PER_THREAD = 500
    }
}
