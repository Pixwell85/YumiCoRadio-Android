// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The equaliser's response, checked numerically. A wrong coefficient sign is inaudible to reason
 * about through a streaming pipeline, so it is caught here instead: the magnitude of the cascade at
 * probe frequencies has to be what the design asks for.
 */
class BiquadTest {

    private val fs = 44100.0

    @Test
    fun `all bands flat is unity everywhere`() {
        val cascade = EqualizerSpec.cascade(EqualizerSpec.ZERO_GAINS, fs)
        for (f in listOf(30.0, 60.0, 200.0, 1000.0, 5000.0, 15000.0, 20000.0)) {
            assertEquals(0.0, Biquad.magnitudeDb(cascade, f, fs), 0.01, "not unity at $f Hz")
        }
    }

    @Test
    fun `a peaking band boosts its own centre and rolls off`() {
        // Only 1 kHz (band 4, a peaking band) at +12; every other band flat.
        val gains = EqualizerSpec.ZERO_GAINS.toMutableList().apply { this[4] = 12 }
        val cascade = EqualizerSpec.cascade(gains, fs)

        // At the centre the boost is the design gain, near exactly.
        assertEquals(12.0, Biquad.magnitudeDb(cascade, 1000.0, fs), 0.2, "wrong gain at centre")
        // Two octaves out on each side it has largely rolled off — and, crucially, is still a boost,
        // never a cut (the sign check that catches a flipped coefficient).
        val low = Biquad.magnitudeDb(cascade, 250.0, fs)
        val high = Biquad.magnitudeDb(cascade, 4000.0, fs)
        assertTrue(low in 0.0..6.0, "250 Hz off-centre response $low out of range")
        assertTrue(high in 0.0..6.0, "4 kHz off-centre response $high out of range")
    }

    @Test
    fun `a peaking cut is symmetric to a boost`() {
        val boost = EqualizerSpec.ZERO_GAINS.toMutableList().apply { this[4] = 12 }
        val cut = EqualizerSpec.ZERO_GAINS.toMutableList().apply { this[4] = -12 }
        val gAtCentreBoost = Biquad.magnitudeDb(EqualizerSpec.cascade(boost, fs), 1000.0, fs)
        val gAtCentreCut = Biquad.magnitudeDb(EqualizerSpec.cascade(cut, fs), 1000.0, fs)
        assertEquals(12.0, gAtCentreBoost, 0.2)
        assertEquals(-12.0, gAtCentreCut, 0.2)
    }

    @Test
    fun `the low shelf lifts the bass and leaves the top alone`() {
        val gains = EqualizerSpec.ZERO_GAINS.toMutableList().apply { this[0] = 12 } // 60 Hz low shelf
        val cascade = EqualizerSpec.cascade(gains, fs)
        // On the shelf, near the design gain; well above it, back to flat.
        assertEquals(12.0, Biquad.magnitudeDb(cascade, 30.0, fs), 1.0, "low shelf plateau wrong")
        assertEquals(0.0, Biquad.magnitudeDb(cascade, 8000.0, fs), 0.5, "low shelf leaked into the top")
    }

    @Test
    fun `the high shelf lifts the treble and leaves the bass alone`() {
        val gains = EqualizerSpec.ZERO_GAINS.toMutableList().apply { this[9] = 12 } // 16 kHz high shelf
        val cascade = EqualizerSpec.cascade(gains, fs)
        assertEquals(12.0, Biquad.magnitudeDb(cascade, 20000.0, fs), 1.5, "high shelf plateau wrong")
        assertEquals(0.0, Biquad.magnitudeDb(cascade, 200.0, fs), 0.5, "high shelf leaked into the bass")
    }

    @Test
    fun `every site preset is ten bands`() {
        EqualizerSpec.PRESETS.forEach { (name, gains) ->
            assertEquals(EqualizerSpec.BAND_COUNT, gains.size, "$name has the wrong band count")
        }
        assertEquals(22, EqualizerSpec.PRESETS.size)
        assertEquals(EqualizerSpec.ZERO_GAINS, EqualizerSpec.PRESETS["Flat"])
    }
}
