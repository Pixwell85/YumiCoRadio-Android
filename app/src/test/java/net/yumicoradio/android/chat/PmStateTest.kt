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
    fun `a received pm opens a conversation and marks it unread`() {
        val s = PmState().received("Yumi", msg("Yumi", "hey"))
        assertTrue("Yumi" in s.conversations.keys)
        assertTrue("Yumi" in s.open, "a received pm must leave a button behind")
        assertTrue("Yumi" in s.unread)
        assertEquals(listOf("hey"), s.messages("Yumi").map { it.text })
    }

    @Test
    fun `a pm received into the window being looked at is not unread`() {
        val s = PmState().opened("Yumi").received("Yumi", msg("Yumi", "hey"))
        assertTrue("Yumi" !in s.unread)
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
    fun `dismissing removes the conversation entirely`() {
        val s = PmState().received("Yumi", msg("Yumi", "hey")).opened("Yumi").dismissed("Yumi")
        assertTrue("Yumi" !in s.open)
        assertNull(s.active)
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
