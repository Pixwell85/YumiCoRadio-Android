// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertEquals

/**
 * Drives the processor on the JVM the way the audio sink does — configure, flush, queue, drain —
 * to prove the bypass path returns the audio untouched rather than silence.
 */
@UnstableApi
class EqualizerProcessorTest {

    private fun pcm(vararg samples: Short): ByteBuffer {
        val b = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { b.putShort(it) }
        b.flip()
        return b
    }

    @Test
    fun `bypass passes 16-bit pcm through unchanged`() {
        Equalizer.setEnabled(false) // coeffs == null → bypass
        val p = EqualizerProcessor()
        p.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        p.flush()

        val input = pcm(100, -200, 3000, -4000, 12345, -12345)
        val expected = input.duplicate()
        p.queueInput(input)
        val out = p.output

        assertEquals(expected.remaining(), out.remaining(), "output byte count differs from input")
        val e = expected.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val o = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        while (e.hasRemaining()) {
            assertEquals(e.get(), o.get(), "sample differs in bypass")
        }
    }

    @Test
    fun `empty input is a no-op and yields no output`() {
        Equalizer.setEnabled(false)
        val p = EqualizerProcessor()
        p.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        p.flush()

        p.queueInput(ByteBuffer.allocate(0))
        // The pipeline feeds an empty buffer once drained; the processor must not manufacture output
        // (that tripped a sink checkState → ERROR_CODE_FAILED_RUNTIME_CHECK).
        assertEquals(false, p.output.hasRemaining())
    }
}
