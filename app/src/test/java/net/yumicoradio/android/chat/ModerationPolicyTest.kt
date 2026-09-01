// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModerationPolicyTest {
    private fun user(nick: String, role: String? = null, moderator: Boolean = false) =
        ChatUser(nickname = nick, role = role, moderator = moderator)

    @Test fun `moderator receives only the approved temporary actions`() {
        val actions = ModerationPolicy.actionsFor(
            user("WinDark99", role = "voice", moderator = true),
            user("Guest"),
        )
        assertEquals(
            listOf(
                ModerationAction.KICK,
                ModerationAction.MUTE_5M,
                ModerationAction.MUTE_30M,
                ModerationAction.MUTE_1H,
                ModerationAction.BAN_24H,
                ModerationAction.RESET_QUOTA,
            ),
            actions,
        )
    }

    @Test fun `administrator also receives permanent ban`() {
        val actions = ModerationPolicy.actionsFor(user("Shiro", role = "admin"), user("Guest"))
        assertTrue(ModerationAction.BAN_PERMANENT in actions)
    }

    @Test fun `moderator cannot target an administrator or self`() {
        val moderator = user("WinDark99", role = "voice", moderator = true)
        assertTrue(ModerationPolicy.actionsFor(moderator, user("Shiro", role = "admin")).isEmpty())
        assertTrue(ModerationPolicy.actionsFor(moderator, moderator).isEmpty())
    }

    @Test fun `ordinary users cannot moderate or toggle uploads`() {
        val guest = user("Guest")
        assertTrue(ModerationPolicy.actionsFor(guest, user("Other")).isEmpty())
        assertFalse(ModerationPolicy.canToggleUploads(guest))
        assertTrue(ModerationPolicy.canToggleUploads(user("WinDark99", moderator = true)))
    }
}
