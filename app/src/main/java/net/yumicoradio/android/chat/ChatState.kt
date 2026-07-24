// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ChatMessage

/**
 * The mIRC-style view model: every channel keeps its own buffer, only the active one is on screen,
 * and the others raise an unread flag.
 *
 * Immutable, so the repository can hand each new value straight to a StateFlow, and so this is
 * testable without a socket or a coroutine.
 */
data class ChatState(
    val active: ChatChannel = ChatChannel.DEFAULT,
    val buffers: Map<ChatChannel, List<ChatMessage>> = emptyMap(),
    val unread: Set<ChatChannel> = emptySet(),
) {
    fun buffer(channel: ChatChannel): List<ChatMessage> = buffers[channel].orEmpty()

    /**
     * Files [msg] into its channel — or into every channel when it is a server notice — and raises
     * the unread flag on any channel that is not the one being looked at.
     */
    fun received(msg: ChatMessage): ChatState {
        val targets = if (msg.allChannels) ChatChannel.entries.toList() else listOf(msg.channel)
        val nextBuffers = buffers.toMutableMap()
        targets.forEach { channel ->
            nextBuffers[channel] = (buffer(channel) + msg).takeLast(MAX_PER_CHANNEL)
        }
        return copy(
            buffers = nextBuffers,
            unread = unread + targets.filter { it != active },
        )
    }

    fun switchedTo(channel: ChatChannel): ChatState = copy(active = channel, unread = unread - channel)

    companion object {
        /** The server keeps no history, so an uncapped buffer is a slow leak with nothing to gain. */
        const val MAX_PER_CHANNEL = 500
    }
}
