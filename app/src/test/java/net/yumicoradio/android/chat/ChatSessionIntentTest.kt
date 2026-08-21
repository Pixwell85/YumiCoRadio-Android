// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlinx.coroutines.runBlocking
import net.yumicoradio.android.chat.model.ConnectionState
import net.yumicoradio.android.chat.model.NickState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSessionIntentTest {

    @Test fun `restorer primes a remembered password before reconnecting`() = runBlocking {
        val calls = mutableListOf<String>()
        val restorer = ChatSessionRestorer(
            loadPassword = { nick -> calls += "load:$nick"; "encrypted-password" },
            primePassword = { password -> calls += "prime:$password" },
            connect = { nick -> calls += "connect:$nick" },
        )

        val restored = restorer.restore(
            stayConnected = true,
            sessionWanted = true,
            savedNick = "Shiro",
            connection = ConnectionState.DISCONNECTED,
            nick = NickState.Idle,
        )

        assertTrue(restored)
        assertEquals(
            listOf("load:Shiro", "prime:encrypted-password", "connect:Shiro"),
            calls,
        )
    }

    @Test fun `restorer reconnects an ordinary nickname without priming a password`() = runBlocking {
        val calls = mutableListOf<String>()
        val restorer = ChatSessionRestorer(
            loadPassword = { nick -> calls += "load:$nick"; null },
            primePassword = { password -> calls += "prime:$password" },
            connect = { nick -> calls += "connect:$nick" },
        )

        assertTrue(
            restorer.restore(
                stayConnected = true,
                sessionWanted = true,
                savedNick = "Listener",
                connection = ConnectionState.DISCONNECTED,
                nick = NickState.Idle,
            ),
        )
        assertEquals(listOf("load:Listener", "connect:Listener"), calls)
    }

    @Test fun `restorer does nothing after explicit disconnect cleared session intent`() = runBlocking {
        val calls = mutableListOf<String>()
        val restorer = ChatSessionRestorer(
            loadPassword = { calls += "load"; null },
            primePassword = { calls += "prime" },
            connect = { calls += "connect" },
        )

        val restored = restorer.restore(
            stayConnected = true,
            sessionWanted = false,
            savedNick = "Shiro",
            connection = ConnectionState.DISCONNECTED,
            nick = NickState.Idle,
        )

        assertFalse(restored)
        assertTrue(calls.isEmpty())
    }
}
