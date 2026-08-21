// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatVideoControlsStateTest {

    @Test
    fun `controls follow the native player visibility`() {
        val state = ChatVideoControlsState()
        assertTrue(state.visible)

        state.controllerVisibilityChanged(isVisible = false)
        assertFalse(state.visible)

        state.controllerVisibilityChanged(isVisible = true)
        assertTrue(state.visible)
    }

    @Test
    fun `controls use the requested two second timeout`() {
        assertEquals(2_000, ChatVideoControlsState.AUTO_HIDE_MILLIS)
    }

    @Test
    fun `active custom interaction holds controls after native timeout`() {
        val state = ChatVideoControlsState()
        state.interactionChanged(isActive = true)

        state.controllerVisibilityChanged(isVisible = false)

        assertTrue(state.visible)
        state.interactionChanged(isActive = false)
        assertFalse(state.visible)
    }

    @Test
    fun `overlapping interactions hold controls until all interactions finish`() {
        val state = ChatVideoControlsState()
        state.controllerVisibilityChanged(isVisible = false)

        state.interactionChanged(isActive = true)
        state.interactionChanged(isActive = true)
        state.interactionChanged(isActive = false)

        assertTrue(state.visible)
        state.interactionChanged(isActive = false)
        assertFalse(state.visible)
    }
}
