// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import java.util.Calendar
import java.util.TimeZone
import net.yumicoradio.android.ratings.RankingPage
import net.yumicoradio.android.ratings.RankingPeriod
import net.yumicoradio.android.ratings.RankingPeriodType
import net.yumicoradio.android.ratings.RankingTab
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RankingsSelectionTest {
    private val paris = TimeZone.getTimeZone("Europe/Paris")

    @Test fun `current week anchor is its Monday`() {
        val thursday = Calendar.getInstance(paris).apply {
            clear(); set(2026, Calendar.SEPTEMBER, 3, 12, 0)
        }
        assertEquals("2026-08-31", currentRankingAnchor(RankingPeriodType.WEEK, thursday))
    }

    @Test fun `one tab tap selects and loads only that tab`() {
        val oldPage = page(RankingTab.LIKE, RankingPeriodType.DAY, "2026-09-04", current = true)
        val selected = RankingsUiState(page = oldPage).selectRankingTab(RankingTab.DISLIKE)

        assertEquals(RankingTab.DISLIKE, selected.tab)
        assertEquals(false, selected.showingMyVotes)
        assertNull(selected.page)
    }

    @Test fun `refresh advances a current daily view after local midnight`() {
        val nextDay = Calendar.getInstance(paris).apply {
            clear(); set(2026, Calendar.SEPTEMBER, 4, 0, 1)
        }
        val state = RankingsUiState(
            anchor = "2026-09-03",
            page = page(RankingTab.LIKE, RankingPeriodType.DAY, "2026-09-03", current = true),
        )

        val refreshed = state.advanceCurrentPeriod(nextDay)

        assertEquals("2026-09-04", refreshed.anchor)
        assertNull(refreshed.page)
    }

    private fun page(tab: RankingTab, type: RankingPeriodType, key: String, current: Boolean) = RankingPage(
        tab = tab,
        period = RankingPeriod(type, key, 1, 2, current, false),
        nextResetMs = 2,
        page = 1,
        totalRows = 0,
        totalPages = 0,
        rows = emptyList(),
    )
}
