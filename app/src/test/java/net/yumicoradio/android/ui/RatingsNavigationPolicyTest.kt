// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingsNavigationPolicyTest {
    @Test fun `returning to player refreshes the current vote`() {
        assertTrue(shouldRefreshVote(Screen.Rankings, Screen.Player))
        assertTrue(shouldRefreshVote(Screen.MyVotes, Screen.Player))
        assertFalse(shouldRefreshVote(Screen.Player, Screen.Player))
    }

    @Test fun `my votes remains available without an account`() {
        assertTrue(canOpenMyVotes(signedIn = false))
        assertTrue(canOpenMyVotes(signedIn = true))
    }
}
