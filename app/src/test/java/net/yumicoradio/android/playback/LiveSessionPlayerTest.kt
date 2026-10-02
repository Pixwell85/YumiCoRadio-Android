// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSessionPlayerTest {
    @Test
    fun `car pause keeps the station resumable instead of stopping it`() {
        val backend = RecordingBackend()
        val sessionPlayer = LiveSessionPlayer(unusedDelegate(), LiveStreamPlayback(backend))

        sessionPlayer.pause()

        assertEquals(listOf("pause"), backend.calls)
    }

    @Test
    fun `car play after pause reconnects to live edge`() {
        val backend = RecordingBackend()
        val sessionPlayer = LiveSessionPlayer(unusedDelegate(), LiveStreamPlayback(backend))

        sessionPlayer.pause()
        sessionPlayer.play()

        assertEquals(listOf("pause", "stop", "replace", "prepare", "play"), backend.calls)
    }

    @Test
    fun `setPlayWhenReady false pauses but explicit Stop still closes the stream`() {
        val backend = RecordingBackend()
        val sessionPlayer = LiveSessionPlayer(unusedDelegate(), LiveStreamPlayback(backend))

        sessionPlayer.setPlayWhenReady(false)
        sessionPlayer.stop()

        assertEquals(listOf("pause", "stop"), backend.calls)
    }

    private fun unusedDelegate(): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
    ) { _, method, _ -> error("Unexpected delegate call: ${method.name}") } as Player
}

private class RecordingBackend : LiveStreamBackend {
    val calls = mutableListOf<String>()
    override fun stop() { calls += "stop" }
    override fun pause() { calls += "pause" }
    override fun replaceStream() { calls += "replace" }
    override fun prepare() { calls += "prepare" }
    override fun play() { calls += "play" }
}
