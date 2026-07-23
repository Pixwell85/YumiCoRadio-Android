package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The decision layer behind the background-notification service. These guard the bug that shipped
 * unnoticed: public-channel messages never notified, because the service's flow could not fire for
 * a user who received no private message.
 */
class ChatNotificationsTest {

    private fun msg(user: String, text: String, channel: ChatChannel = ChatChannel.GENERAL) =
        ChatMessage(user = user, text = text, type = "chat", channel = channel)

    private fun stateWith(vararg messages: ChatMessage): ChatState {
        var s = ChatState()
        messages.forEach { s = s.received(it) }
        return s
    }

    @Test
    fun `priming records the backlog so it is not replayed`() {
        val state = stateWith(msg("Alice", "hi"), msg("Bob", "yo"))
        val seen = ChatNotifications.seed(state, PmState())

        // Same state on the next tick: nothing new to surface.
        val decision = ChatNotifications.advance(seen, state, PmState(), NotificationMode.ALL, "me")
        assertTrue(decision.toNotify.isEmpty())
    }

    @Test
    fun `a new public message notifies — the case that was silently dead`() {
        val before = stateWith(msg("Alice", "hi"))
        val seen = ChatNotifications.seed(before, PmState())

        val after = before.received(msg("Bob", "anyone around?"))
        val decision = ChatNotifications.advance(seen, after, PmState(), NotificationMode.ALL, "me")

        assertEquals(1, decision.toNotify.size)
        assertEquals("Bob", decision.toNotify[0].message.user)
        assertEquals("ch:general", decision.toNotify[0].key)
    }

    @Test
    fun `a message in a non-active channel still notifies`() {
        // active = general, but the new line lands in music.
        val before = stateWith(msg("Alice", "hi", ChatChannel.GENERAL))
        val seen = ChatNotifications.seed(before, PmState())

        val after = before.received(msg("Bob", "track id?", ChatChannel.MUSIC))
        val decision = ChatNotifications.advance(seen, after, PmState(), NotificationMode.ALL, "me")

        assertEquals(1, decision.toNotify.size)
        assertEquals("ch:music", decision.toNotify[0].key)
    }

    @Test
    fun `a public line and a PM from the same user do not evict each other`() {
        // The single-global-fingerprint bug: notify public, then PM, then re-emit the same state.
        val state = stateWith(msg("Bob", "hello all"))
        val pm = PmState().received("Bob", msg("Bob", "hey you"))

        val first = ChatNotifications.advance(emptyMap(), state, pm, NotificationMode.ALL, "me")
        assertEquals(setOf("ch:general", "pm:Bob"), first.toNotify.map { it.key }.toSet())

        // Nothing changed since: a re-emission must surface nothing.
        val second = ChatNotifications.advance(first.seen, state, pm, NotificationMode.ALL, "me")
        assertTrue(second.toNotify.isEmpty())
    }

    @Test
    fun `system notices advance seen but never notify`() {
        val state = ChatState().received(
            ChatMessage("Server", "Alice joined", type = "system", channel = ChatChannel.GENERAL, allChannels = true),
        )
        val decision = ChatNotifications.advance(emptyMap(), state, PmState(), NotificationMode.ALL, "me")
        assertTrue(decision.toNotify.isEmpty())
        // Seen was advanced for every channel the notice was filed into.
        assertTrue(decision.seen.containsKey("ch:general"))
    }

    @Test
    fun `a message that arrived while muted does not notify retroactively on unmute`() {
        val before = stateWith(msg("Alice", "hi"))
        val seen0 = ChatNotifications.seed(before, PmState())

        // New line arrives while muted: nothing notified, but it is now seen.
        val after = before.received(msg("Bob", "still here?"))
        val muted = ChatNotifications.advance(seen0, after, PmState(), NotificationMode.NONE, "me")
        assertTrue(muted.toNotify.isEmpty())

        // User flips to ALL; the same tail must not buzz for the message they missed while muted.
        val unmuted = ChatNotifications.advance(muted.seen, after, PmState(), NotificationMode.ALL, "me")
        assertTrue(unmuted.toNotify.isEmpty())
    }
}
