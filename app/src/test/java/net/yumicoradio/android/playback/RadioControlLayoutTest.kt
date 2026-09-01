// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import net.yumicoradio.android.ratings.VoteChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioControlLayoutTest {
    @Test
    fun `playing layout is like stop dislike`() {
        val layout = radioControlLayout(isPlaying = true, vote = VoteChoice.NONE)

        assertEquals(
            listOf(RadioControlAction.LIKE, RadioControlAction.STOP, RadioControlAction.DISLIKE),
            layout.map { it.action },
        )
    }

    @Test
    fun `stopped layout restores play in the centre`() {
        val layout = radioControlLayout(isPlaying = false, vote = VoteChoice.NONE)

        assertEquals(RadioControlAction.PLAY, layout[1].action)
    }

    @Test
    fun `layout carries the current vote state`() {
        val layout = radioControlLayout(isPlaying = true, vote = VoteChoice.DISLIKE)

        assertEquals(false, layout[0].active)
        assertEquals(true, layout[2].active)
    }
}
