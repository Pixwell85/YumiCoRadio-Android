// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import net.yumicoradio.android.chat.model.ChatMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatStateTest {

    private fun msg(
        text: String,
        channel: ChatChannel = ChatChannel.GENERAL,
        all: Boolean = false,
    ) = ChatMessage("Yumi", text, "message", channel, all)

    @Test
    fun `a message lands in its own channel only`() {
        val s = ChatState().received(msg("hi", ChatChannel.MUSIC))
        assertEquals(listOf("hi"), s.buffer(ChatChannel.MUSIC).map { it.text })
        assertTrue(s.buffer(ChatChannel.GENERAL).isEmpty())
    }

    @Test
    fun `an allChannels notice lands in every buffer`() {
        val s = ChatState().received(msg("Shiro joined", ChatChannel.GENERAL, all = true))
        ChatChannel.entries.forEach { assertEquals(1, s.buffer(it).size, "missing in $it") }
    }

    @Test
    fun `a message to another channel marks it unread`() {
        val s = ChatState().received(msg("hi", ChatChannel.MUSIC))
        assertTrue(ChatChannel.MUSIC in s.unread)
    }

    @Test
    fun `a message to the active channel does not mark it unread`() {
        val s = ChatState().received(msg("hi", ChatChannel.GENERAL))
        assertTrue(ChatChannel.GENERAL !in s.unread)
    }

    @Test
    fun `an allChannels notice never marks the active channel unread`() {
        val s = ChatState().received(msg("Shiro joined", ChatChannel.MUSIC, all = true))
        assertTrue(ChatChannel.GENERAL !in s.unread)
        assertTrue(ChatChannel.MUSIC in s.unread)
    }

    @Test
    fun `switching to a channel clears its unread flag and makes it active`() {
        val s = ChatState().received(msg("hi", ChatChannel.MUSIC)).switchedTo(ChatChannel.MUSIC)
        assertEquals(ChatChannel.MUSIC, s.active)
        assertTrue(ChatChannel.MUSIC !in s.unread)
    }

    @Test
    fun `a buffer is capped and drops the oldest first`() {
        var s = ChatState()
        repeat(ChatState.MAX_PER_CHANNEL + 10) { i -> s = s.received(msg("m$i")) }
        val buffer = s.buffer(ChatChannel.GENERAL)
        assertEquals(ChatState.MAX_PER_CHANNEL, buffer.size)
        assertEquals("m10", buffer.first().text)
        assertEquals("m${ChatState.MAX_PER_CHANNEL + 9}", buffer.last().text)
    }

    /**
     * The MOTD-vanishing bug: the repository dispatches each socket event on its own coroutine on a
     * multi-threaded scope, and the handlers all mutated `_state`. With a read-modify-write
     * (`_state.value = _state.value.received(x)`) two events that land together each read the same
     * base and the later write clobbers the earlier — so the MOTD, arriving in the same burst as the
     * join messages, was overwritten. `update {}` is a compare-and-set loop, so nothing is lost.
     *
     * This drives the same StateFlow with `update {}` from many threads at once and asserts every
     * message survives. Swapping `update {}` for `value = value.received(...)` makes it fail.
     */
    @Test
    fun `concurrent updates never lose a message`() = runBlocking {
        val flow = MutableStateFlow(ChatState())
        val n = 500
        (0 until n).map { i ->
            async(Dispatchers.Default) {
                flow.update { it.received(msg("m$i")) }
            }
        }.awaitAll()

        assertEquals(n, flow.value.buffer(ChatChannel.GENERAL).size)
    }
}
