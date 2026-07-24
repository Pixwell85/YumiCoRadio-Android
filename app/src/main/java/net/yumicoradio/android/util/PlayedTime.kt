// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Formats a Last.fm scrobble timestamp for the history list. Pure + deterministic (zone/locale injected). */
object PlayedTime {
    fun label(
        uts: Long?,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
        locale: Locale = Locale.US,
    ): String {
        if (uts == null || uts <= 0L) return "now"
        val thenMs = uts * 1000
        val calThen = Calendar.getInstance(zone, locale).apply { timeInMillis = thenMs }
        val calNow = Calendar.getInstance(zone, locale).apply { timeInMillis = nowMillis }
        val sameDay = calThen.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
            calThen.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "dd/MM HH:mm"
        return SimpleDateFormat(pattern, locale).apply { timeZone = zone }.format(Date(thenMs))
    }
}
