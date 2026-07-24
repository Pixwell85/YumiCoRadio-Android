// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class ReconnectPolicyTest {
    private val p = ReconnectPolicy(baseMs = 1000, maxMs = 30_000)
    @Test fun backs_off_exponentially() {
        assertEquals(1000, p.delayForAttempt(1))
        assertEquals(2000, p.delayForAttempt(2))
        assertEquals(4000, p.delayForAttempt(3))
        assertEquals(8000, p.delayForAttempt(4))
    }
    @Test fun caps_at_max() {
        assertEquals(30_000, p.delayForAttempt(10))
    }
    @Test fun attempt_below_one_is_base() {
        assertEquals(1000, p.delayForAttempt(0))
    }
}
