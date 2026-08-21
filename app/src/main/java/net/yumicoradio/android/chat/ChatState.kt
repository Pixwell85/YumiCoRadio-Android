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
    val lastPublicChannel: ChatChannel = ChatChannel.DEFAULT,
    val buffers: Map<ChatChannel, List<ChatMessage>> = emptyMap(),
    val presenceHistory: List<ChatMessage> = emptyList(),
    val separatePresenceActivity: Boolean = false,
    val messageUnread: Set<ChatChannel> = emptySet(),
    val presenceUnread: Set<ChatChannel> = emptySet(),
    val nextLocalOrder: Long = 0,
) {
    val unread: Set<ChatChannel> get() = messageUnread + presenceUnread

    /** Derives the visible channel from regular messages plus the canonical presence history. */
    fun buffer(channel: ChatChannel): List<ChatMessage> {
        val presence = when {
            separatePresenceActivity && channel == ChatChannel.ACTIVITY -> presenceHistory
            !separatePresenceActivity && channel.serverBacked -> presenceHistory
            else -> emptyList()
        }
        return (buffers[channel].orEmpty() + presence)
            .sortedBy { it.localOrder }
            .takeLast(MAX_PER_CHANNEL)
    }

    /**
     * Files [msg] into its channel — or into every channel when it is a server notice — and raises
     * the unread flag on any channel that is not the one being looked at.
     */
    fun received(msg: ChatMessage): ChatState {
        val localOrder = msg.localOrder.takeIf { it > 0 } ?: (nextLocalOrder + 1)
        val orderedMessage = msg.copy(localOrder = localOrder)
        val targets = if (msg.allChannels) {
            ChatChannel.entries.filter { it.serverBacked }
        } else {
            listOf(msg.channel)
        }
        val nextBuffers = buffers.toMutableMap()
        targets.forEach { channel ->
            nextBuffers[channel] = (buffers[channel].orEmpty() + orderedMessage)
                .sortedBy { it.localOrder }
                .takeLast(MAX_PER_CHANNEL)
        }
        return copy(
            buffers = nextBuffers,
            messageUnread = messageUnread + targets.filter { it != active },
            nextLocalOrder = maxOf(nextLocalOrder, localOrder),
        )
    }

    /** Retains presence once, then derives either Activity or regular-channel views from it. */
    fun receivedPresence(msg: ChatMessage): ChatState {
        val localOrder = msg.localOrder.takeIf { it > 0 } ?: (nextLocalOrder + 1)
        val targets = if (separatePresenceActivity) {
            listOf(ChatChannel.ACTIVITY)
        } else {
            ChatChannel.entries.filter { it.serverBacked }
        }
        val newPresenceUnread = targets.filter { it != active }.toSet()
        return copy(
            presenceHistory = (presenceHistory + msg.copy(localOrder = localOrder))
                .sortedBy { it.localOrder }
                .takeLast(MAX_PER_CHANNEL),
            presenceUnread = presenceUnread + newPresenceUnread,
            nextLocalOrder = maxOf(nextLocalOrder, localOrder),
        )
    }

    /** Changes only the derived view; retained messages stay untouched and in timestamp order. */
    fun withPresenceRouting(enabled: Boolean): ChatState = copy(
        active = if (!enabled && active == ChatChannel.ACTIVITY) lastPublicChannel else active,
        separatePresenceActivity = enabled,
        presenceUnread = emptySet(),
    )

    fun switchedTo(channel: ChatChannel): ChatState {
        if (channel == ChatChannel.ACTIVITY && !separatePresenceActivity) return this
        return copy(
            active = channel,
            lastPublicChannel = if (channel.serverBacked) channel else lastPublicChannel,
            messageUnread = messageUnread - channel,
            presenceUnread = presenceUnread - channel,
        )
    }

    /** Clears all local public messages and presence; private conversations are stored elsewhere. */
    fun clearedPublicHistory(): ChatState = copy(
        buffers = emptyMap(),
        presenceHistory = emptyList(),
        messageUnread = emptySet(),
        presenceUnread = emptySet(),
    )

    companion object {
        /** The server keeps no history, so an uncapped buffer is a slow leak with nothing to gain. */
        const val MAX_PER_CHANNEL = 500
    }
}

/** The Activity selector does not exist in the UI while its derived view is disabled. */
fun visibleChatChannels(activityEnabled: Boolean): List<ChatChannel> =
    ChatChannel.entries.filter { activityEnabled || it != ChatChannel.ACTIVITY }
