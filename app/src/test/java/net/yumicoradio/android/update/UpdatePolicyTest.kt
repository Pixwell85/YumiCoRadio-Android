// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.update

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatePolicyTest {
    @Test
    fun `automatic checks require opt-in`() {
        assertFalse(UpdatePolicy.shouldCheckAutomatically(false, now = 100_000, lastAttempt = 0))
        assertTrue(UpdatePolicy.shouldCheckAutomatically(true, now = 100_000, lastAttempt = 0))
    }

    @Test
    fun `automatic checks run at most once per day`() {
        val last = 1_000L
        assertFalse(
            UpdatePolicy.shouldCheckAutomatically(
                true,
                now = last + UpdatePolicy.INTERVAL_MS - 1,
                lastAttempt = last,
            ),
        )
        assertTrue(
            UpdatePolicy.shouldCheckAutomatically(
                true,
                now = last + UpdatePolicy.INTERVAL_MS,
                lastAttempt = last,
            ),
        )
    }

    @Test
    fun `automatic dialog stays dismissed until a newer code appears`() {
        assertFalse(UpdatePolicy.shouldShowAutomatically(availableCode = 116, dismissedCode = 116))
        assertFalse(UpdatePolicy.shouldShowAutomatically(availableCode = 115, dismissedCode = 116))
        assertTrue(UpdatePolicy.shouldShowAutomatically(availableCode = 117, dismissedCode = 116))
    }
}
