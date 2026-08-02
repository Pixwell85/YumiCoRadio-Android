// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One biquad's coefficients, already normalised so `a0 == 1`. A cascade of ten of these is the
 * whole equaliser — the same shapes the website builds with Web Audio `BiquadFilterNode`s.
 *
 * Pure data and pure math, deliberately free of any Android type, so the response can be checked
 * numerically in a plain unit test before a single sample flows through the pipeline.
 */
data class BiquadCoeffs(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    companion object {
        /** Passes everything through untouched — the bypass and the zero-gain band. */
        val IDENTITY = BiquadCoeffs(1.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * RBJ Audio-EQ-Cookbook filter design, matched to Web Audio's defaults so the app sounds like the
 * site: peaking bands at Q = 1 (Web Audio's default, which `equalizer.js` never overrides), and
 * shelves at the spec's fixed slope (S = 1).
 */
object Biquad {

    private const val DEFAULT_Q = 1.0

    /** Peaking EQ centred on [f0], boosting/cutting by [gainDb]. */
    fun peaking(f0: Double, fs: Double, gainDb: Double, q: Double = DEFAULT_Q): BiquadCoeffs {
        val a = amp(gainDb)
        val w0 = omega(f0, fs)
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)

        val b0 = 1 + alpha * a
        val b1 = -2 * cw
        val b2 = 1 - alpha * a
        val a0 = 1 + alpha / a
        val a1 = -2 * cw
        val a2 = 1 - alpha / a
        return normalise(b0, b1, b2, a0, a1, a2)
    }

    /** Low shelf below [f0] (S = 1). */
    fun lowShelf(f0: Double, fs: Double, gainDb: Double): BiquadCoeffs {
        val a = amp(gainDb)
        val w0 = omega(f0, fs)
        val cw = cos(w0)
        val twoSqrtAAlpha = shelfAlpha(a, sin(w0))

        val b0 = a * ((a + 1) - (a - 1) * cw + twoSqrtAAlpha)
        val b1 = 2 * a * ((a - 1) - (a + 1) * cw)
        val b2 = a * ((a + 1) - (a - 1) * cw - twoSqrtAAlpha)
        val a0 = (a + 1) + (a - 1) * cw + twoSqrtAAlpha
        val a1 = -2 * ((a - 1) + (a + 1) * cw)
        val a2 = (a + 1) + (a - 1) * cw - twoSqrtAAlpha
        return normalise(b0, b1, b2, a0, a1, a2)
    }

    /** High shelf above [f0] (S = 1). */
    fun highShelf(f0: Double, fs: Double, gainDb: Double): BiquadCoeffs {
        val a = amp(gainDb)
        val w0 = omega(f0, fs)
        val cw = cos(w0)
        val twoSqrtAAlpha = shelfAlpha(a, sin(w0))

        val b0 = a * ((a + 1) + (a - 1) * cw + twoSqrtAAlpha)
        val b1 = -2 * a * ((a - 1) + (a + 1) * cw)
        val b2 = a * ((a + 1) + (a - 1) * cw - twoSqrtAAlpha)
        val a0 = (a + 1) - (a - 1) * cw + twoSqrtAAlpha
        val a1 = 2 * ((a - 1) - (a + 1) * cw)
        val a2 = (a + 1) - (a - 1) * cw - twoSqrtAAlpha
        return normalise(b0, b1, b2, a0, a1, a2)
    }

    /**
     * The cascade's magnitude at [freq], in dB. Series filters multiply, so the dBs add — this is
     * the number a test compares against the site's `getFrequencyResponse`.
     */
    fun magnitudeDb(cascade: List<BiquadCoeffs>, freq: Double, fs: Double): Double {
        val w = omega(freq, fs)
        val cw = cos(w); val c2w = cos(2 * w)
        val sw = sin(w); val s2w = sin(2 * w)
        var db = 0.0
        for (c in cascade) {
            val numRe = c.b0 + c.b1 * cw + c.b2 * c2w
            val numIm = -(c.b1 * sw + c.b2 * s2w)
            val denRe = 1.0 + c.a1 * cw + c.a2 * c2w
            val denIm = -(c.a1 * sw + c.a2 * s2w)
            val mag = sqrt((numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm))
            db += 20 * log10(mag)
        }
        return db
    }

    private fun amp(gainDb: Double) = Math.pow(10.0, gainDb / 40.0)
    private fun omega(f0: Double, fs: Double) = 2.0 * PI * f0 / fs
    private fun shelfAlpha(a: Double, sinW0: Double): Double {
        // alpha = sin(w0)/2 * sqrt((A + 1/A)(1/S - 1) + 2), with S = 1 the bracket is 2.
        val alpha = sinW0 / 2.0 * sqrt(2.0)
        return 2.0 * sqrt(a) * alpha
    }

    private fun normalise(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
}
