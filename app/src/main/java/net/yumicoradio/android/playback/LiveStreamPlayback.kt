// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

/** Minimal boundary that makes discarding the old live connection enforceable and testable. */
internal interface LiveStreamBackend {
    fun stop()
    fun pause()
    fun replaceStream()
    fun prepare()
    fun play()
}

/** A radio has no useful paused position: every start creates a new connection at the live edge. */
internal class LiveStreamPlayback(private val backend: LiveStreamBackend) {
    private var transitioning = false

    fun stop() = transition { backend.stop() }

    /** Keep the session resumable across temporary interruptions; playLive still reconnects. */
    fun pause() = transition { backend.pause() }

    fun playLive() = transition {
        backend.stop()
        backend.replaceStream()
        backend.prepare()
        backend.play()
    }

    private inline fun transition(block: () -> Unit) {
        if (transitioning) return
        transitioning = true
        try { block() } finally { transitioning = false }
    }
}
