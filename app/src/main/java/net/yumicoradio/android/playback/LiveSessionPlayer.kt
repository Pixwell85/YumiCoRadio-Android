// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/** Keep temporary pauses resumable while every Play starts a fresh live stream. */
internal class LiveSessionPlayer(
    delegate: Player,
    private val livePlayback: LiveStreamPlayback,
) : ForwardingPlayer(delegate) {
    override fun play() = livePlayback.playLive()
    override fun pause() = livePlayback.pause()
    override fun stop() = livePlayback.stop()
    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) livePlayback.playLive() else livePlayback.pause()
    }
}
