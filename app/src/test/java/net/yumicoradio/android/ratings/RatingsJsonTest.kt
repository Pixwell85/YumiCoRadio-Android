// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingsJsonTest {
    @Test fun `parses current vote and reset boundary`() {
        val value = Json.parseToJsonElement("""{
          "track":{"trackId":"track-1","artist":"Artist","title":"Title","artworkUrl":null},
          "occurrenceId":"occurrence-1","choice":"like","changed":true,
          "ballot":{"weekKey":"2026-08-31","startMs":10,"endMs":20},"nextResetMs":20
        }""")
        val vote = parseCurrentVote(value)
        assertEquals(VoteChoice.LIKE, vote.choice)
        assertEquals(20, vote.nextResetMs)
    }

    @Test fun `rejects non HTTPS artwork`() {
        val value = Json.parseToJsonElement("""{
          "track":{"trackId":"track-1","artist":"Artist","title":"Title","artworkUrl":"http://bad"},
          "occurrenceId":"occurrence-1","choice":"none","changed":false,
          "ballot":{"weekKey":"2026-08-31","startMs":10,"endMs":20},"nextResetMs":20
        }""")
        assertTrue(runCatching { parseCurrentVote(value) }.exceptionOrNull() is RatingsApiException)
    }
}
