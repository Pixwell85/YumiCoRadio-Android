// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.history

import org.junit.Test
import kotlin.test.assertEquals

class HistoryActionsTest {
    private val artist = "Mariya Takeuchi"
    private val title = "Plastic Love & More"

    @Test
    fun `copy text uses artist dash title`() {
        assertEquals(
            "Mariya Takeuchi - Plastic Love & More",
            HistoryActions.displayText(artist, title),
        )
    }

    @Test
    fun `search targets encode the complete display text`() {
        val query = HistoryActions.displayText(artist, title)
        assertEquals(
            "https://www.youtube.com/results?search_query=Mariya+Takeuchi+-+Plastic+Love+%26+More",
            HistoryActions.youtubeUrl(query),
        )
        assertEquals(
            "https://www.google.com/search?q=Mariya+Takeuchi+-+Plastic+Love+%26+More",
            HistoryActions.googleUrl(query),
        )
        assertEquals(
            "https://open.spotify.com/search/Mariya%20Takeuchi%20-%20Plastic%20Love%20%26%20More",
            HistoryActions.spotifyUrl(query),
        )
    }
}
