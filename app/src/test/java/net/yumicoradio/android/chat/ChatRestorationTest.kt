// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ConnectionState
import net.yumicoradio.android.chat.model.NickState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatRestorationTest {

    @Test fun `chat entry waits until the saved nickname is loaded`() {
        assertEquals(
            ChatEntryAction.WAIT,
            chatEntryAction(ConnectionState.DISCONNECTED, NickState.Idle, savedNick = null),
        )
    }

    @Test fun `chat entry asks only after a loaded nickname is known to be blank`() {
        assertEquals(
            ChatEntryAction.ASK_NICKNAME,
            chatEntryAction(ConnectionState.DISCONNECTED, NickState.Idle, savedNick = ""),
        )
    }

    @Test fun `chat entry joins a loaded saved nickname`() {
        assertEquals(
            ChatEntryAction.JoinNickname("Shiro"),
            chatEntryAction(ConnectionState.DISCONNECTED, NickState.Idle, savedNick = "Shiro"),
        )
    }

    @Test fun `chat entry does nothing when a connection or nickname session already exists`() {
        assertEquals(
            ChatEntryAction.NONE,
            chatEntryAction(ConnectionState.CONNECTING, NickState.Idle, savedNick = "Shiro"),
        )
        assertEquals(
            ChatEntryAction.NONE,
            chatEntryAction(
                ConnectionState.DISCONNECTED,
                NickState.Joined("Shiro"),
                savedNick = "Shiro",
            ),
        )
    }

    @Test fun `startup restores only an intentional idle background session`() {
        assertEquals(
            "Shiro",
            chatRestoreNickname(
                stayConnected = true,
                sessionWanted = true,
                savedNick = " Shiro ",
                connection = ConnectionState.DISCONNECTED,
                nick = NickState.Idle,
            ),
        )
        assertNull(chatRestoreNickname(true, false, "Shiro", ConnectionState.DISCONNECTED, NickState.Idle))
        assertNull(chatRestoreNickname(false, true, "Shiro", ConnectionState.DISCONNECTED, NickState.Idle))
        assertNull(chatRestoreNickname(true, true, "", ConnectionState.DISCONNECTED, NickState.Idle))
        assertNull(chatRestoreNickname(true, true, "Shiro", ConnectionState.CONNECTING, NickState.Idle))
        assertNull(
            chatRestoreNickname(
                true,
                true,
                "Shiro",
                ConnectionState.DISCONNECTED,
                NickState.Joined("Shiro"),
            ),
        )
    }
}
