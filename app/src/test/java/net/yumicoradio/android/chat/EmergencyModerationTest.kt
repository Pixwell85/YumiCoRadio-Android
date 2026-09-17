// SPDX-License-Identifier: GPL-3.0-or-later
package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencyModerationTest {
    @Test fun `message parser retains opaque server id`() {
        val message = ChatProtocol.parseMessage(JSONObject("""{"user":"A","text":"hello","type":"user","channel":"general","messageId":"opaque-id"}"""))!!
        assertEquals("opaque-id", message.messageId)
    }

    @Test fun `cleanup commands never grant moderator access to admins`() {
        val moderator = ChatUser("Helper", moderator = true)
        val admin = ChatUser("Yumi", role = "admin")
        assertTrue(ModerationPolicy.actionsFor(moderator, admin).isEmpty())
        val command = ChatProtocol.moderationCommand(ModerationAction.KICK_DELETE, "Spammer")
        assertEquals("mod:kick", command.event)
        assertTrue(command.payload.getBoolean("deleteMessages"))
    }
}
