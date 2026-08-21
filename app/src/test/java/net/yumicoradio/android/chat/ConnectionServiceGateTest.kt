// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.NickState
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionServiceGateTest {

    private val joined = NickState.Joined("yumi")

    @Test fun `no session means no service even with stay-connected on`() {
        assertFalse(shouldRunConnectionService(stayConnected = true, nick = NickState.Idle, transferHold = false))
        assertFalse(shouldRunConnectionService(stayConnected = true, nick = NickState.NeedsNick, transferHold = false))
        assertFalse(shouldRunConnectionService(stayConnected = true, nick = NickState.Rejected("yumi", "taken"), transferHold = false))
    }

    @Test fun `stay-connected plus a session runs the service`() {
        assertTrue(shouldRunConnectionService(stayConnected = true, nick = joined, transferHold = false))
        assertTrue(shouldRunConnectionService(stayConnected = true, nick = NickState.Joining("yumi"), transferHold = false))
        assertTrue(shouldRunConnectionService(stayConnected = true, nick = NickState.NeedsPassword("yumi"), transferHold = false))
    }

    @Test fun `a session alone does not run the service without the preference`() {
        assertFalse(shouldRunConnectionService(stayConnected = false, nick = joined, transferHold = false))
    }

    @Test fun `a transfer hold runs the service regardless of preference or session`() {
        assertTrue(shouldRunConnectionService(stayConnected = false, nick = NickState.Idle, transferHold = true))
        assertTrue(shouldRunConnectionService(stayConnected = false, nick = joined, transferHold = true))
    }

    @Test fun `a transient drop keeps the session as Joined, so the service stays up`() {
        // socket.io reports a network blip as DISCONNECTED but leaves nick untouched. The gate looks
        // at nick, not connection, so the service is not torn down mid-reconnect.
        assertTrue(shouldRunConnectionService(stayConnected = true, nick = joined, transferHold = false))
    }

    @Test fun `an intentional session keeps the service alive during process restoration`() {
        assertTrue(
            shouldRunConnectionService(
                stayConnected = true,
                nick = NickState.Idle,
                transferHold = false,
                sessionWanted = true,
            ),
        )
    }

    @Test fun `restoration intent still respects stay-connected`() {
        assertFalse(
            shouldRunConnectionService(
                stayConnected = false,
                nick = NickState.Idle,
                transferHold = false,
                sessionWanted = true,
            ),
        )
    }
}
