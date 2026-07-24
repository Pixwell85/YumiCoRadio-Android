// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer

/**
 * The playing stream's per-channel level, read straight off the decoded audio.
 *
 * **Why not `android.media.audiofx.Visualizer`:** that API requires `RECORD_AUDIO`. A radio player
 * asking for the microphone would be a permission flag on F-Droid and an entirely reasonable reason
 * to distrust the app — for a decoration. Media3's [TeeAudioProcessor] sits inside our own playback
 * chain instead, sees the PCM we are already decoding, and needs no permission at all.
 *
 * `TeeAudioProcessor` rather than a hand-written `AudioProcessor`: it is Media3's own passthrough,
 * so the audio itself cannot be altered by a mistake here. The worst a bug in this file can do is
 * show a wrong number.
 *
 * Levels are written from the playback thread and read from the UI's frame loop. Plain volatile
 * floats on purpose: no allocation and no locking on the audio path, and a frame reading a value one
 * buffer out of date is invisible.
 */
@UnstableApi
object AudioLevels {

    @Volatile
    var left: Float = 0f
        private set

    @Volatile
    var right: Float = 0f
        private set

    // The buffer format arrives in flush(), not with each buffer, so it has to be remembered.
    @Volatile
    private var channelCount: Int = 0

    @Volatile
    private var measurable: Boolean = false

    /** Cleared on stop so the meter falls to silence instead of freezing at its last reading. */
    fun reset() {
        left = 0f
        right = 0f
    }

    /** The sink handed to [TeeAudioProcessor]. It only measures, and never retains the buffer. */
    val sink: TeeAudioProcessor.AudioBufferSink = object : TeeAudioProcessor.AudioBufferSink {

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            this@AudioLevels.channelCount = channelCount
            // Only 16-bit PCM is measured. Anything else — a float pipeline, a passthrough bitstream
            // — leaves the meter idle rather than showing a number derived from misread bytes.
            measurable = encoding == C.ENCODING_PCM_16BIT
            reset()
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val channels = channelCount
            if (!measurable || channels <= 0) return
            left = VuScale.rms16(buffer, channels, channel = 0)
            right = if (channels > 1) VuScale.rms16(buffer, channels, channel = 1) else left
        }
    }
}
