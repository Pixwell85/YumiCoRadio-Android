// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The equaliser in the playback chain: ten biquads per channel over the decoded 16-bit PCM, the same
 * cascade the website runs in Web Audio. Placed **before** the level tap so the VU meter shows the
 * equalised signal, not the raw one.
 *
 * `onConfigure` does no work beyond noting the format — crucially it never calls back into
 * [Equalizer], because `configure()` runs on the playback thread during sink setup and must not
 * contend with a UI-thread write (that stalled playback). Coefficients are (re)built lazily on the
 * audio thread the first buffer after [Equalizer.generation] changes, from the volatile settings.
 * When the equaliser is off or flat the cascade is null and the buffer is copied through untouched.
 */
@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {

    private var channels = 0
    private var fs = 0
    // [channel][2 * band]: the transposed-direct-form-II accumulators s1, s2 for each biquad.
    private var state: Array<DoubleArray> = emptyArray()

    private var lastGeneration = -1
    private var active: Array<BiquadCoeffs>? = null

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Float output is disabled upstream, so this only ever sees 16-bit PCM; refuse anything else
        // rather than corrupt it, and hand the format straight on so the level tap still gets 16-bit.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        fs = inputAudioFormat.sampleRate
        state = Array(channels) { DoubleArray(2 * EqualizerSpec.BAND_COUNT) }
        lastGeneration = -1 // force a rebuild on the first buffer
        return inputAudioFormat
    }

    override fun onFlush() {
        // A restart (seek, re-buffer at the live edge) must not thump: drop the filter memory.
        for (s in state) s.fill(0.0)
    }

    /** Rebuild the cascade only when the settings actually changed. Runs on the audio thread, but
     *  designing ten biquads costs microseconds and happens once per change, not per buffer. */
    private fun refreshIfChanged() {
        val gen = Equalizer.generation
        if (gen == lastGeneration) return
        lastGeneration = gen
        val gains = Equalizer.gains
        active = if (!Equalizer.enabled || fs <= 0 || gains.all { it == 0 }) {
            null
        } else {
            EqualizerSpec.cascade(gains, fs.toDouble()).toTypedArray()
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        // Match TeeAudioProcessor exactly: on empty input, return WITHOUT touching the output buffer.
        // The pipeline feeds an empty buffer once its input is drained; calling replaceOutputBuffer
        // there produces a spurious output buffer that trips a checkState in the sink
        // (ERROR_CODE_FAILED_RUNTIME_CHECK — play did nothing).
        if (remaining == 0) return

        refreshIfChanged()

        val coeffs = active
        if (coeffs == null || channels == 0) {
            // Bypass, byte-for-byte, the same one line the Tee uses.
            replaceOutputBuffer(remaining).put(inputBuffer).flip()
            return
        }

        // A read-only, little-endian view so the shared input buffer's own order is left untouched.
        val inShorts = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val sampleCount = remaining / 2   // 16-bit: two bytes per interleaved sample
        val output = replaceOutputBuffer(remaining).order(ByteOrder.LITTLE_ENDIAN)

        var i = 0
        while (i < sampleCount) {
            val ch = i % channels
            val filtered = applyCascade(coeffs, state[ch], inShorts.get(i).toDouble())
            // 16-bit wraps on overflow into crackle; clamp, as the browser's own output clips.
            val clamped = filtered.coerceIn(-32768.0, 32767.0).toInt()
            output.putShort(clamped.toShort())
            i++
        }

        inputBuffer.position(inputBuffer.limit())   // signal the whole input was consumed
        output.flip()
    }

    /** One sample through the cascade, TDF-II, updating [st] in place. Allocation-free by design. */
    private fun applyCascade(coeffs: Array<BiquadCoeffs>, st: DoubleArray, input: Double): Double {
        var x = input
        for (b in coeffs.indices) {
            val c = coeffs[b]
            val i1 = 2 * b
            val i2 = i1 + 1
            val y = c.b0 * x + st[i1]
            st[i1] = c.b1 * x - c.a1 * y + st[i2]
            st[i2] = c.b2 * x - c.a2 * y
            x = y
        }
        return x
    }
}
