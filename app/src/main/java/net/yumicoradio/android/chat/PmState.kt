// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

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
    /** Last roster state known for every private correspondent. */
    val availability: Map<String, Boolean> = emptyMap(),
) {
    fun messages(nick: String): List<ChatMessage> = conversations[nick].orEmpty()

    /** Unknown is deliberately treated as offline: a PM must never look deliverable on a guess. */
    fun isOnline(nick: String): Boolean = availability[nick] == true

    /**
     * Someone wrote to us. First contact — or the first message after we closed the thread's button
     * ([from] no longer in [open]) — pops the window over the main chat, so it reads as seen. A
     * message into a thread that is already open but minimised does NOT yank the window back (the
     * PM Dialog covers the main chat, unlike the website's separate floating windows); it just flags
     * unread. A message into the thread currently on screen stays read.
     */
    fun received(from: String, message: ChatMessage): PmState {
        val pops = from !in open
        val onScreen = pops || active == from
        val restored = if (availability[from] == false) {
            append(from, presenceLine("$from is back online."))
        } else {
            this
        }
        return restored.append(from, message).copy(
            open = open + from,
            active = if (pops) from else active,
            unread = if (onScreen) unread - from else unread + from,
            availability = restored.availability + (from to true),
        )
    }

    /**
     * Applies an authoritative user list to correspondents we already know about. Repeated roster
     * snapshots are intentionally silent; only actual online/offline transitions add a system line.
     */
    fun updatedRoster(onlineNicks: Set<String>): PmState {
        val known = conversations.keys + open + listOfNotNull(active)
        return known.fold(this) { state, nick ->
            val online = nick in onlineNicks
            val previous = state.availability[nick]
            val tracked = state.copy(availability = state.availability + (nick to online))
            when {
                previous == true && !online ->
                    tracked.append(nick, presenceLine("$nick has disconnected."))
                previous == false && online ->
                    tracked.append(nick, presenceLine("$nick is back online."))
                else -> tracked
            }
        }
    }

    /** Records a failed attempt without ever adding the outgoing text as if it had arrived. */
    fun deliveryFailed(nick: String, offline: Boolean): PmState {
        val text = if (offline) {
            "$nick has disconnected. Message not delivered."
        } else {
            "Message delivery could not be confirmed. Please try again."
        }
        val state = if (offline) copy(availability = availability + (nick to false)) else this
        return state.append(nick, presenceLine(text))
    }

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

    /**
     * Closes the window and drops the channel-bar button, but keeps the thread's messages so
     * reopening it (from the user list, or on a new incoming PM) shows the kept history. Content
     * only goes away when the app process ends — the website keeps it until disconnect the same way.
     */
    fun hidden(nick: String): PmState = copy(
        open = open - nick,
        unread = unread - nick,
        active = if (active == nick) null else active,
    )

    private fun append(nick: String, message: ChatMessage): PmState =
        copy(conversations = conversations + (nick to (messages(nick) + message).takeLast(MAX_PER_THREAD)))

    private fun presenceLine(text: String) = ChatMessage(
        user = "System",
        text = text,
        type = "system",
        channel = net.yumicoradio.android.chat.model.ChatChannel.GENERAL,
    )

    companion object {
        /** Same reasoning as the channel buffers: nothing is stored server-side to reload. */
        const val MAX_PER_THREAD = 500
    }
}
