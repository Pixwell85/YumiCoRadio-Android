// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import java.util.Calendar
import java.util.TimeZone
import net.yumicoradio.android.R
import net.yumicoradio.android.ratings.RankingPeriodType
import net.yumicoradio.android.ratings.RankingTab
import net.yumicoradio.android.ratings.VoteChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class RankingPresentationTest {
    private val paris = TimeZone.getTimeZone("Europe/Paris")

    @Test
    fun `daily countdown ends at the next local midnight`() {
        val now = localTime(2026, Calendar.SEPTEMBER, 16, 22, 30, 15)

        assertEquals("Daily reset in 01:29:45", rankingResetLabel(RankingPeriodType.DAY, now, paris))
    }

    @Test
    fun `weekly countdown ends at the next local Monday`() {
        val now = localTime(2026, Calendar.SEPTEMBER, 16, 22, 30, 15)

        assertEquals("Weekly reset in 4d 01:29:45", rankingResetLabel(RankingPeriodType.WEEK, now, paris))
    }

    @Test
    fun `monthly countdown ends at the first day of the next month`() {
        val now = localTime(2026, Calendar.SEPTEMBER, 30, 23, 59, 58)

        assertEquals("Monthly reset in 00:00:02", rankingResetLabel(RankingPeriodType.MONTH, now, paris))
    }

    @Test
    fun `ranking tabs and personal choices use their matching hearts`() {
        assertEquals(R.drawable.ic_vote_heart, rankingVoteIcon(RankingTab.LIKE))
        assertEquals(R.drawable.ic_vote_heart_broken, rankingVoteIcon(RankingTab.DISLIKE))
        assertEquals(R.drawable.ic_vote_heart, rankingVoteIcon(VoteChoice.LIKE))
        assertEquals(R.drawable.ic_vote_heart_broken, rankingVoteIcon(VoteChoice.DISLIKE))
    }

    private fun localTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance(paris).apply {
            clear()
            set(year, month, day, hour, minute, second)
        }.timeInMillis
}
