// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrackTimeTest {

    @Test
    fun `formats elapsed over duration mid-track`() {
        // started 77s ago, 2:48 long
        assertEquals("1:17 / 2:48", TrackTime.label(playedAt = 1000, duration = 168, nowSeconds = 1077))
    }

    @Test
    fun `pads seconds to two digits`() {
        assertEquals("0:05 / 3:00", TrackTime.label(playedAt = 1000, duration = 180, nowSeconds = 1005))
    }

    @Test
    fun `clamps elapsed to duration when the poll is stale`() {
        // 300s after a 168s track started: the next poll has not landed yet
        assertEquals("2:48 / 2:48", TrackTime.label(playedAt = 1000, duration = 168, nowSeconds = 1300))
    }

    @Test
    fun `never goes negative when played_at is in the future`() {
        // clock skew between device and server
        assertEquals("0:00 / 2:48", TrackTime.label(playedAt = 1100, duration = 168, nowSeconds = 1000))
    }

    @Test
    fun `no duration yields null`() {
        // live DJ broadcast: no track duration to count against
        assertNull(TrackTime.label(playedAt = 1000, duration = 0, nowSeconds = 1077))
    }

    @Test
    fun `no played_at yields null`() {
        assertNull(TrackTime.label(playedAt = 0, duration = 168, nowSeconds = 1077))
    }
}
