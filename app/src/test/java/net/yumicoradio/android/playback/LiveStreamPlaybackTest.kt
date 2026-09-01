// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveStreamPlaybackTest {
    @Test
    fun `stop discards playback instead of pausing it`() {
        val backend = RecordingLiveStreamBackend()

        LiveStreamPlayback(backend).stop()

        assertEquals(listOf("stop"), backend.calls)
    }

    @Test
    fun `play always reconnects to the current live stream`() {
        val backend = RecordingLiveStreamBackend()

        LiveStreamPlayback(backend).playLive()

        assertEquals(listOf("stop", "replace", "prepare", "play"), backend.calls)
    }

    @Test
    fun `player callbacks cannot re-enter a live transition`() {
        val backend = RecordingLiveStreamBackend()
        val playback = LiveStreamPlayback(backend)
        backend.onStop = { playback.stop() }

        playback.stop()

        assertEquals(listOf("stop"), backend.calls)
    }
}

private class RecordingLiveStreamBackend : LiveStreamBackend {
    val calls = mutableListOf<String>()
    var onStop: (() -> Unit)? = null

    override fun stop() { calls += "stop"; onStop?.invoke() }
    override fun replaceStream() { calls += "replace" }
    override fun prepare() { calls += "prepare" }
    override fun play() { calls += "play" }
}
