package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ChatMessage
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPolicyTest {

    private val me = "Shiro"

    private fun msg(user: String, text: String, type: String = "user") =
        ChatMessage(user, text, type, ChatChannel.GENERAL)

    @Test
    fun `off notifies for nothing`() {
        val mode = NotificationMode.NONE
        assertFalse(NotificationPolicy.shouldNotify(msg("Yumi", "Shiro look"), mode, me, isPm = false))
        assertFalse(NotificationPolicy.shouldNotify(msg("Yumi", "hi"), mode, me, isPm = true))
    }

    @Test
    fun `all notifies for any message from someone else`() {
        val mode = NotificationMode.ALL
        assertTrue(NotificationPolicy.shouldNotify(msg("Yumi", "anything"), mode, me, isPm = false))
    }

    @Test
    fun `mentions mode notifies on a private message`() {
        assertTrue(
            NotificationPolicy.shouldNotify(
                msg("Yumi", "you around?"), NotificationMode.MENTIONS, me, isPm = true,
            ),
        )
    }

    @Test
    fun `mentions mode notifies when the nickname appears`() {
        val mode = NotificationMode.MENTIONS
        assertTrue(NotificationPolicy.shouldNotify(msg("Yumi", "hey Shiro"), mode, me, isPm = false))
        assertTrue(NotificationPolicy.shouldNotify(msg("Yumi", "shiro?"), mode, me, isPm = false))
        assertFalse(NotificationPolicy.shouldNotify(msg("Yumi", "hey there"), mode, me, isPm = false))
    }

    /**
     * A nickname inside a longer word is not a mention: "Shiro" must not fire on "Shirokuma", or
     * the mentions mode becomes as noisy as the all mode for anyone with a short nickname.
     */
    @Test
    fun `a nickname inside a longer word is not a mention`() {
        val mode = NotificationMode.MENTIONS
        assertFalse(NotificationPolicy.shouldNotify(msg("Yumi", "Shirokuma"), mode, me, isPm = false))
        assertFalse(NotificationPolicy.shouldNotify(msg("Yumi", "ushiro"), mode, me, isPm = false))
        assertTrue(NotificationPolicy.shouldNotify(msg("Yumi", "ping Shiro!"), mode, me, isPm = false))
    }

    @Test
    fun `your own messages never notify`() {
        assertFalse(NotificationPolicy.shouldNotify(msg(me, "hello all"), NotificationMode.ALL, me, isPm = false))
        assertFalse(NotificationPolicy.shouldNotify(msg(me, "hi"), NotificationMode.MENTIONS, me, isPm = true))
    }

    /**
     * Join and quit notices are the server talking, and every user's arrival would otherwise buzz
     * the phone in the all mode.
     */
    @Test
    fun `system and motd lines never notify`() {
        val mode = NotificationMode.ALL
        assertFalse(
            NotificationPolicy.shouldNotify(
                msg("System", "Yumi joined the chat.", "system"), mode, me, isPm = false,
            ),
        )
        assertFalse(
            NotificationPolicy.shouldNotify(
                ChatMessage("MOTD", "Welcome!", "system", ChatChannel.GENERAL), mode, me, isPm = false,
            ),
        )
    }

    @Test
    fun `an empty nickname cannot be mentioned`() {
        assertFalse(
            NotificationPolicy.shouldNotify(msg("Yumi", "anything"), NotificationMode.MENTIONS, "", isPm = false),
        )
    }
}
