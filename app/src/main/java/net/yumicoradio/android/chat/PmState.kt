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
) {
    fun messages(nick: String): List<ChatMessage> = conversations[nick].orEmpty()

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
        return append(from, message).copy(
            open = open + from,
            active = if (pops) from else active,
            unread = if (onScreen) unread - from else unread + from,
        )
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

    companion object {
        /** Same reasoning as the channel buffers: nothing is stored server-side to reload. */
        const val MAX_PER_THREAD = 500
    }
}
