// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatMessage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PmStateTest {

    private fun msg(user: String, text: String) =
        ChatMessage(user, text, "user", net.yumicoradio.android.chat.model.ChatChannel.GENERAL)

    @Test
    fun `a received pm auto-opens the conversation on screen`() {
        val s = PmState().received("Yumi", msg("Yumi", "hey"))
        assertTrue("Yumi" in s.conversations.keys)
        assertTrue("Yumi" in s.open, "a received pm must leave a button behind")
        assertEquals("Yumi", s.active, "a received pm pops over the main chat")
        assertTrue("Yumi" !in s.unread, "an auto-opened thread is on screen, so not unread")
        assertEquals(listOf("hey"), s.messages("Yumi").map { it.text })
    }

    @Test
    fun `a pm received into the window being looked at is not unread`() {
        val s = PmState().opened("Yumi").received("Yumi", msg("Yumi", "hey"))
        assertTrue("Yumi" !in s.unread)
    }

    @Test
    fun `a pm into a minimised thread flags unread without yanking the window back`() {
        // Alice's thread is open but minimised (we went back to the main window).
        val s = PmState()
            .received("Alice", msg("Alice", "hi"))     // first contact: pops, active = Alice
            .closed()                                  // minimise: active null, button kept
            .received("Alice", msg("Alice", "again"))  // into the minimised thread
        assertNull(s.active, "an already-open thread must not pop back over the main chat")
        assertTrue("Alice" in s.unread, "it flags unread instead")
        assertEquals(2, s.messages("Alice").size)
    }

    @Test
    fun `sending records the message locally`() {
        // The server does not echo outgoing PMs, so the client must keep its own copy or the
        // conversation shows only one side.
        val s = PmState().sent("Yumi", msg("Shiro", "yo"))
        assertEquals(listOf("yo"), s.messages("Yumi").map { it.text })
        assertTrue("Yumi" !in s.unread, "your own message must not mark the thread unread")
    }

    @Test
    fun `opening a conversation clears its unread flag and shows it`() {
        val s = PmState().received("Yumi", msg("Yumi", "hey")).opened("Yumi")
        assertEquals("Yumi", s.active)
        assertTrue("Yumi" !in s.unread)
    }

    @Test
    fun `closing hides the window but keeps the conversation and its button`() {
        val s = PmState().received("Yumi", msg("Yumi", "hey")).opened("Yumi").closed()
        assertNull(s.active, "closing must return to the main window")
        assertTrue("Yumi" in s.open, "the button must survive so the thread can be reopened")
        assertEquals(1, s.messages("Yumi").size, "history must survive closing")
    }

    @Test
    fun `hiding removes the button but keeps the conversation`() {
        val s = PmState().received("Yumi", msg("Yumi", "hey")).hidden("Yumi")
        assertTrue("Yumi" !in s.open, "the channel-bar button must go")
        assertTrue("Yumi" !in s.unread)
        assertNull(s.active, "the window closes")
        assertEquals(1, s.messages("Yumi").size, "history must survive hiding")
    }

    @Test
    fun `a hidden conversation reopens with its history`() {
        val s = PmState().received("Yumi", msg("Yumi", "hey")).hidden("Yumi").opened("Yumi")
        assertEquals("Yumi", s.active)
        assertTrue("Yumi" in s.open)
        assertEquals(listOf("hey"), s.messages("Yumi").map { it.text })
    }

    @Test
    fun `several conversations are kept apart`() {
        val s = PmState()
            .received("Yumi", msg("Yumi", "one"))
            .received("Bob", msg("Bob", "two"))
        assertEquals(listOf("one"), s.messages("Yumi").map { it.text })
        assertEquals(listOf("two"), s.messages("Bob").map { it.text })
        assertEquals(setOf("Yumi", "Bob"), s.open)
    }

    @Test
    fun `a conversation is capped like a channel buffer`() {
        var s = PmState()
        repeat(PmState.MAX_PER_THREAD + 5) { i -> s = s.received("Yumi", msg("Yumi", "m$i")) }
        val thread = s.messages("Yumi")
        assertEquals(PmState.MAX_PER_THREAD, thread.size)
        assertEquals("m5", thread.first().text)
    }

    @Test
    fun `an unknown conversation has no messages rather than throwing`() {
        assertTrue(PmState().messages("Nobody").isEmpty())
    }
}
