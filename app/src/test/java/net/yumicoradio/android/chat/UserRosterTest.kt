// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser
import net.yumicoradio.android.chat.model.NickState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserRosterTest {

    private fun user(
        nick: String,
        role: String? = null,
        bot: Boolean = false,
        moderator: Boolean = false,
    ) = ChatUser(nickname = nick, role = role, bot = bot, moderator = moderator)

    @Test
    fun `badges follow role and bot flag`() {
        assertEquals(UserRoster.Badge.ADMIN, UserRoster.badge(user("Zed", role = "admin")))
        assertEquals(UserRoster.Badge.MODERATOR, UserRoster.badge(user("Mod", moderator = true)))
        assertEquals(UserRoster.Badge.BOT, UserRoster.badge(user("YumiTG", bot = true)))
        assertEquals(UserRoster.Badge.VOICE, UserRoster.badge(user("Reg", role = "voice")))
        assertEquals(UserRoster.Badge.VOICE, UserRoster.badge(user("NewAccount", role = "user")))
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
    fun `sort is admins then moderators then bots then voice then the rest`() {
        val users = listOf(
            user("bob"),
            user("alice"),
            user("YumiTG", bot = true),
            user("Reg", role = "voice"),
            user("Zed", role = "admin"),
            user("Amy", role = "admin"),
            user("WinDark99", role = "voice", moderator = true),
        )
        assertEquals(
            listOf("Amy", "Zed", "WinDark99", "YumiTG", "Reg", "alice", "bob"),
            UserRoster.sorted(users).map { it.nickname },
        )
    }

    @Test
    fun `reserved option accepts every authoritative account or legacy reserved role`() {
        assertTrue(UserRoster.isCurrentNicknameReserved(NickState.Joined("Owner"), listOf(user("Owner", "admin"))))
        assertTrue(UserRoster.isCurrentNicknameReserved(NickState.Joined("Member"), listOf(user("Member", "voice"))))
        assertTrue(UserRoster.isCurrentNicknameReserved(NickState.Joined("Account"), listOf(user("Account", "user"))))
        assertFalse(UserRoster.isCurrentNicknameReserved(NickState.Joined("Guest"), listOf(user("Guest"))))
        assertFalse(UserRoster.isCurrentNicknameReserved(NickState.Joined("Guest"), listOf(user("Guest", "null"))))
    }

    @Test
    fun `reserved option stays hidden while disconnected or still joining`() {
        val users = listOf(user("Owner", "voice"))
        assertFalse(UserRoster.isCurrentNicknameReserved(NickState.Idle, users))
        assertFalse(UserRoster.isCurrentNicknameReserved(NickState.Joining("Owner"), users))
    }

    @Test
    fun `transient reconnect preserves a joined reserved nickname`() {
        // Socket.IO leaves NickState.Joined intact during a transport reconnect; the role remains
        // authoritative even while ConnectionState temporarily reports disconnected.
        assertTrue(
            UserRoster.isCurrentNicknameReserved(
                NickState.Joined("OWNER"),
                listOf(user("owner", "voice")),
            ),
        )
    }

    @Test
    fun `changing to an ordinary nickname does not inherit the previous reserved role`() {
        val users = listOf(user("Owner", "voice"), user("Guest"))
        assertFalse(UserRoster.isCurrentNicknameReserved(NickState.Joined("Guest"), users))
    }
}
