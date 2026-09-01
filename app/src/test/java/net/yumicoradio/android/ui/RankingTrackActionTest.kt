// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import net.yumicoradio.android.ratings.MyVoteRow
import net.yumicoradio.android.ratings.RankingRow
import net.yumicoradio.android.ratings.RatingTrack
import net.yumicoradio.android.ratings.VoteChoice
import org.junit.Test
import kotlin.test.assertEquals

class RankingTrackActionTest {
    private val track = RatingTrack(
        trackId = "mariya-takeuchi-plastic-love",
        artist = "Mariya Takeuchi",
        title = "Plastic Love",
        artworkUrl = "https://example.test/cover.jpg",
    )

    @Test
    fun `ranking entries open actions for their displayed track`() {
        val row = RankingRow(rank = 1, track = track, count = 8, visitorHasMatchingVote = true)

        assertEquals(
            TrackActionTarget("Mariya Takeuchi", "Plastic Love"),
            row.trackActionTarget(),
        )
    }

    @Test
    fun `my votes entries open actions for their displayed track`() {
        val row = MyVoteRow(
            track = track,
            latestChoice = VoteChoice.LIKE,
            currentChoice = VoteChoice.LIKE,
            currentWeekKey = "2026-W36",
            currentWeekEndMs = 1L,
            likedWeeks = 1,
            dislikedWeeks = 0,
            firstVoteMs = 1L,
            latestVoteMs = 1L,
        )

        assertEquals(
            TrackActionTarget("Mariya Takeuchi", "Plastic Love"),
            row.trackActionTarget(),
        )
    }
}
