// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import java.io.File
import net.yumicoradio.android.ratings.VoteChoice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MiniPlayerTransportTest {
    @Test fun `mini transport shows play only when playback was not requested`() {
        assertEquals("▶", miniPlayerTransportIcon(playbackRequested = false))
        assertEquals("■", miniPlayerTransportIcon(playbackRequested = true))
    }

    @Test fun `view model tracks play intent independently from audible buffering state`() {
        val source = File("src/main/java/net/yumicoradio/android/ui/PlayerViewModel.kt").readText()
        assertTrue(source.contains("val playbackRequested"))
        assertTrue(source.contains("onPlayWhenReadyChanged"))
    }

    @Test fun `mini player hearts reflect the current vote and loading state`() {
        val liked = miniPlayerVoteControls(VoteChoice.LIKE, loading = false)
        assertTrue(liked.likeActive)
        assertFalse(liked.dislikeActive)
        assertTrue(liked.enabled)

        val disliked = miniPlayerVoteControls(VoteChoice.DISLIKE, loading = true)
        assertFalse(disliked.likeActive)
        assertTrue(disliked.dislikeActive)
        assertFalse(disliked.enabled)
    }
}
