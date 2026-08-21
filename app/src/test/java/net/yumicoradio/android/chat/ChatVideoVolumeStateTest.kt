// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatVideoVolumeStateTest {

    @Test
    fun `volume is clamped to the player range`() {
        val state = ChatVideoVolumeState()

        assertEquals(1f, state.set(2f), 0f)
        assertEquals(0f, state.set(-1f), 0f)
    }

    @Test
    fun `mute restores the previous audible level`() {
        val state = ChatVideoVolumeState()
        state.set(0.35f)

        assertEquals(0f, state.toggleMute(), 0f)
        assertEquals(0.35f, state.toggleMute(), 0f)
    }

    @Test
    fun `unmuting a video that started silent restores full volume`() {
        val state = ChatVideoVolumeState(initialVolume = 0f)

        assertEquals(1f, state.toggleMute(), 0f)
    }
}
