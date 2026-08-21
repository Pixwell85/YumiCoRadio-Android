// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatVideoSessionStateTest {

    @Test
    fun `first video stores the original radio intent`() {
        val state = ChatVideoSessionState()

        state.start("one.mp4", radioWasPlaying = true)

        assertEquals("one.mp4", state.activeUrl)
        assertTrue(state.shouldResumeRadio)
    }

    @Test
    fun `switching videos preserves the original radio intent`() {
        val state = ChatVideoSessionState()
        state.start("one.mp4", radioWasPlaying = true)

        state.switchTo("two.mp4")

        assertEquals("two.mp4", state.activeUrl)
        assertTrue(state.shouldResumeRadio)
    }

    @Test
    fun `stale offscreen event cannot stop the active video`() {
        val state = ChatVideoSessionState()
        state.start("two.mp4", radioWasPlaying = true)

        assertFalse(state.finish("one.mp4"))
        assertEquals("two.mp4", state.activeUrl)
        assertTrue(state.shouldResumeRadio)
    }

    @Test
    fun `finishing active video returns and clears resume intent`() {
        val state = ChatVideoSessionState()
        state.start("one.mp4", radioWasPlaying = true)

        assertTrue(state.finish("one.mp4"))

        assertNull(state.activeUrl)
        assertFalse(state.shouldResumeRadio)
    }

    @Test
    fun `finishing video that did not pause radio never requests resume`() {
        val state = ChatVideoSessionState()
        state.start("one.mp4", radioWasPlaying = false)

        assertFalse(state.finish("one.mp4"))
        assertNull(state.activeUrl)
        assertFalse(state.shouldResumeRadio)
    }

    @Test
    fun `same URL in two messages still has one unambiguous active target`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("first", "same.mp4"), radioWasPlaying = true)

        state.switchTo(ChatVideoTarget("second", "same.mp4"))

        assertEquals("second", state.activeTarget?.key)
        assertFalse(state.finish("first"))
        assertEquals("second", state.activeTarget?.key)
    }

    @Test
    fun `external handoff clears the inline target and defers radio resume`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("message", "video.mp4"), radioWasPlaying = true)

        assertTrue(state.handoffToExternal("message"))

        assertNull(state.activeTarget)
        assertFalse(state.shouldResumeRadio)
    }

    @Test
    fun `stale external handoff leaves the active video untouched`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("current", "video.mp4"), radioWasPlaying = true)

        assertFalse(state.handoffToExternal("stale"))

        assertEquals("current", state.activeTarget?.key)
        assertTrue(state.shouldResumeRadio)
    }

    @Test
    fun `fullscreen keeps the active video alive when its message leaves the viewport`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("message", "video.mp4"), radioWasPlaying = true)

        assertTrue(state.enterFullscreen("message"))

        assertEquals("message", state.fullscreenKey)
        assertFalse(state.shouldReleaseWhenHidden("message"))
    }

    @Test
    fun `exiting fullscreen restores offscreen release protection`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("message", "video.mp4"), radioWasPlaying = true)
        state.enterFullscreen("message")

        state.exitFullscreen()

        assertNull(state.fullscreenKey)
        assertTrue(state.shouldReleaseWhenHidden("message"))
    }

    @Test
    fun `fullscreen remembers source disposal and releases on exit`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("message", "video.mp4"), radioWasPlaying = true)
        state.enterFullscreen("message")

        assertFalse(state.updateVisibility("message", visible = false))

        assertTrue(state.exitFullscreen())
    }

    @Test
    fun `fullscreen exit keeps playback when source is still visible`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("message", "video.mp4"), radioWasPlaying = true)
        state.enterFullscreen("message")

        assertFalse(state.exitFullscreen())
        assertEquals("message", state.activeTarget?.key)
    }

    @Test
    fun `stale message cannot take over fullscreen`() {
        val state = ChatVideoSessionState()
        state.start(ChatVideoTarget("current", "video.mp4"), radioWasPlaying = true)

        assertFalse(state.enterFullscreen("stale"))

        assertNull(state.fullscreenKey)
    }
}
