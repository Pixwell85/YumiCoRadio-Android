// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser
import org.junit.Test
import kotlin.test.assertEquals

class UserRosterTest {

    private fun user(nick: String, role: String? = null, bot: Boolean = false) =
        ChatUser(nickname = nick, role = role, bot = bot)

    @Test
    fun `badges follow role and bot flag`() {
        assertEquals(UserRoster.Badge.ADMIN, UserRoster.badge(user("Zed", role = "admin")))
        assertEquals(UserRoster.Badge.BOT, UserRoster.badge(user("YumiTG", bot = true)))
        assertEquals(UserRoster.Badge.VOICE, UserRoster.badge(user("Reg", role = "voice")))
        assertEquals(UserRoster.Badge.NONE, UserRoster.badge(user("Guest")))
    }

    @Test
    fun `known admins are badged before the server confirms a role`() {
        assertEquals(UserRoster.Badge.ADMIN, UserRoster.badge(user("shiro")))
        assertEquals(UserRoster.Badge.ADMIN, UserRoster.badge(user("Pixwell")))
        // A server-sent role wins over the fallback: a demoted default admin is not still an admin.
        assertEquals(UserRoster.Badge.VOICE, UserRoster.badge(user("Yumi", role = "voice")))
    }

    @Test
    fun `sort is admins then bots then voice then the rest, alphabetical within`() {
        val users = listOf(
            user("bob"),
            user("alice"),
            user("YumiTG", bot = true),
            user("Reg", role = "voice"),
            user("Zed", role = "admin"),
            user("Amy", role = "admin"),
        )
        assertEquals(
            listOf("Amy", "Zed", "YumiTG", "Reg", "alice", "bob"),
            UserRoster.sorted(users).map { it.nickname },
        )
    }
}
