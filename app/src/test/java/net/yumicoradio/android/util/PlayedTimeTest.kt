// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.util

import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayedTimeTest {
    private val utc = TimeZone.getTimeZone("UTC")
    // 1700000000 = 2023-11-14T22:13:20Z
    private val uts = 1700000000L

    @Test fun null_or_zero_is_now() {
        assertEquals("now", PlayedTime.label(null, uts * 1000, utc, Locale.US))
        assertEquals("now", PlayedTime.label(0L, uts * 1000, utc, Locale.US))
    }
    @Test fun same_day_shows_hh_mm() {
        assertEquals("22:13", PlayedTime.label(uts, uts * 1000, utc, Locale.US))
    }
    @Test fun other_day_shows_date_and_time() {
        val now = (uts + 2 * 86400) * 1000
        assertEquals("14/11 22:13", PlayedTime.label(uts, now, utc, Locale.US))
    }
}
