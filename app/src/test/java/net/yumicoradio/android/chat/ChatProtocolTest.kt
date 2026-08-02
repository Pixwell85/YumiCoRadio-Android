// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatChannel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatProtocolTest {

    @Test
    fun `parses a normal message`() {
        val json = JSONObject()
            .put("user", "Yumi").put("text", "hello").put("type", "message").put("channel", "music")
        val msg = ChatProtocol.parseMessage(json)!!
        assertEquals("Yumi", msg.user)
        assertEquals("hello", msg.text)
        assertEquals(ChatChannel.MUSIC, msg.channel)
        assertTrue(!msg.allChannels)
    }

    @Test
    fun `an unknown or missing channel falls back to general`() {
        val missing = JSONObject().put("user", "Yumi").put("text", "hi").put("type", "message")
        assertEquals(ChatChannel.GENERAL, ChatProtocol.parseMessage(missing)!!.channel)

        val bogus = JSONObject()
            .put("user", "Yumi").put("text", "hi").put("type", "message").put("channel", "nope")
        assertEquals(ChatChannel.GENERAL, ChatProtocol.parseMessage(bogus)!!.channel)
    }

    @Test
    fun `system notices carry the allChannels flag`() {
        val json = JSONObject()
            .put("user", "System").put("text", "Shiro joined the chat.")
            .put("type", "system").put("allChannels", true)
        val msg = ChatProtocol.parseMessage(json)!!
        assertTrue(msg.allChannels)
        assertTrue(msg.isSystem)
    }

    @Test
    fun `a frame with no text is dropped rather than throwing`() {
        assertNull(ChatProtocol.parseMessage(JSONObject().put("user", "Yumi")))
    }

    @Test
    fun `parses the user list and skips malformed entries`() {
        val arr = JSONArray()
            .put(JSONObject().put("nickname", "Shiro").put("color", "#ff0000"))
            .put(JSONObject().put("nickname", "Yumi"))
            .put(JSONObject().put("colour", "no nickname here"))
        val users = ChatProtocol.parseUserList(arr)
        assertEquals(listOf("Shiro", "Yumi"), users.map { it.nickname })
        assertEquals("#ff0000", users[0].color)
        assertNull(users[1].color)
    }

    /**
     * The MOTD is an array of `{text, type}` objects per channel, not a string. Reading it with
     * `optString` produced the raw JSON on screen.
     */
    @Test
    fun `parses the motd as typed lines per channel`() {
        val json = JSONObject()
            .put(
                "general",
                JSONArray()
                    .put(JSONObject().put("text", "Welcome to Live Chat!").put("type", "system"))
                    .put(JSONObject().put("text", "Be nice.").put("type", "system")),
            )
            .put(
                "music",
                JSONArray().put(JSONObject().put("text", "Requests welcome").put("type", "info")),
            )

        val motd = ChatProtocol.parseMotd(json)

        assertEquals(2, motd[ChatChannel.GENERAL]!!.size)
        assertEquals("Welcome to Live Chat!", motd[ChatChannel.GENERAL]!![0].text)
        assertEquals("system", motd[ChatChannel.GENERAL]!![0].type)
        assertEquals("info", motd[ChatChannel.MUSIC]!![0].type)
        // The renderer keys off this to drop the `<nick>` prefix, so the two must agree.
        assertTrue(motd[ChatChannel.GENERAL]!!.all { it.isMotd }, "MOTD lines are not marked as such")
        assertEquals(ChatChannel.MUSIC, motd[ChatChannel.MUSIC]!![0].channel)
        assertNull(motd[ChatChannel.SHITPOSTING])
    }

    @Test
    fun `a motd given as a plain string still works`() {
        val json = JSONObject().put("general", "Welcome!")
        val lines = ChatProtocol.parseMotd(json)[ChatChannel.GENERAL]!!
        assertEquals(1, lines.size)
        assertEquals("Welcome!", lines[0].text)
    }

    @Test
    fun `join payload omits the password when there is none`() {
        assertTrue(!ChatProtocol.joinPayload("Shiro", null).has("password"))
        assertEquals("hunter2", ChatProtocol.joinPayload("Shiro", "hunter2").getString("password"))
    }

    @Test
    fun `parses the role when the server sends one, null otherwise`() {
        val arr = JSONArray()
            .put(JSONObject().put("nickname", "Shiro").put("role", "admin"))
            .put(JSONObject().put("nickname", "Bob").put("role", "voice"))
            .put(JSONObject().put("nickname", "Guest"))
        val users = ChatProtocol.parseUserList(arr)
        assertEquals("admin", users[0].role)
        assertEquals("voice", users[1].role)
        assertNull(users[2].role)
    }

    @Test
    fun `parses the bot flag, defaulting to false`() {
        val arr = JSONArray()
            .put(JSONObject().put("nickname", "YumiTG").put("bot", true))
            .put(JSONObject().put("nickname", "Guest"))
        val users = ChatProtocol.parseUserList(arr)
        assertTrue(users[0].bot)
        assertTrue(!users[1].bot)
    }

    @Test
    fun `join payload always announces the reserve capability`() {
        val caps = ChatProtocol.joinPayload("Shiro", null).getJSONArray("caps")
        assertEquals(1, caps.length())
        assertEquals("reserve-v1", caps.getString(0))
    }

    @Test
    fun `isValidNickColor accepts six-digit hex in any case`() {
        assertTrue(ChatProtocol.isValidNickColor("#c33b3b"))
        assertTrue(ChatProtocol.isValidNickColor("#00B4B4"))
    }

    @Test
    fun `isValidNickColor rejects anything else`() {
        assertFalse(ChatProtocol.isValidNickColor(""))
        assertFalse(ChatProtocol.isValidNickColor("c33b3b"))     // no hash
        assertFalse(ChatProtocol.isValidNickColor("#fff"))       // too short
        assertFalse(ChatProtocol.isValidNickColor("#gggggg"))    // non-hex
        assertFalse(ChatProtocol.isValidNickColor("#c33b3b "))   // trailing space
    }

    @Test
    fun `join payload includes a valid colour`() {
        val p = ChatProtocol.joinPayload("bob", null, "#c33b3b")
        assertEquals("#c33b3b", p.getString("color"))
    }

    @Test
    fun `join payload omits an empty or invalid colour`() {
        assertFalse(ChatProtocol.joinPayload("bob", null, "").has("color"))
        assertFalse(ChatProtocol.joinPayload("bob", null, "nope").has("color"))
        assertFalse(ChatProtocol.joinPayload("bob", null, null).has("color"))
    }
}
