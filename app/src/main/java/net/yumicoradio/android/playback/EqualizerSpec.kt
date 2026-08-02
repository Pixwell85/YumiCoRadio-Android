// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

/**
 * The equaliser's fixed shape — bands and presets — copied straight from the website's
 * `equalizer.js` so the two agree band for band and preset for preset.
 *
 * Ten bands: the first a low shelf, the last a high shelf, the eight between them peaking at Q = 1,
 * exactly as the site assigns them. Gains are whole dB in [-12, 12], the range of the site's sliders.
 */
object EqualizerSpec {

    const val MIN_DB = -12
    const val MAX_DB = 12
    const val FLAT = "Flat"

    /** Centre frequencies in Hz, and the short label under each slider. */
    val BANDS: List<Pair<Int, String>> = listOf(
        60 to "60", 170 to "170", 310 to "310", 600 to "600", 1000 to "1k",
        3000 to "3k", 6000 to "6k", 12000 to "12k", 14000 to "14k", 16000 to "16k",
    )

    val BAND_COUNT = BANDS.size

    /** The 22 presets from the site, in its order; `Flat` first, the house `Future Funk` and
     *  `City Pop` last. Each is ten whole-dB gains, one per band. */
    val PRESETS: Map<String, List<Int>> = linkedMapOf(
        "Flat" to listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        "Rock" to listOf(5, 3, -2, -4, -1, 2, 4, 6, 7, 7),
        "Pop" to listOf(-2, -1, 0, 2, 4, 4, 2, 0, -1, -2),
        "Jazz" to listOf(4, 2, 1, 2, -1, -1, 0, 1, 2, 3),
        "Classical" to listOf(5, 4, 3, 2, -2, -2, -1, 0, 1, 2),
        "Electronic" to listOf(6, 5, 1, -2, 0, 3, 4, 5, 5, 4),
        "Dance" to listOf(6, 4, 2, 0, 0, -2, -1, -1, 0, 0),
        "Full Bass" to listOf(7, 6, 5, 3, 1, -2, -3, -4, -5, -6),
        "Full Bass & Treble" to listOf(4, 2, 0, -3, -2, 1, 4, 6, 7, 7),
        "Full Treble" to listOf(-6, -5, -4, -3, -2, 1, 3, 5, 6, 7),
        "Laptop Speakers" to listOf(3, 6, 3, -1, -1, 1, 3, 6, 7, 8),
        "Large Hall" to listOf(6, 6, 3, 3, 0, -2, -2, -2, 0, 0),
        "Live" to listOf(-2, 0, 2, 3, 3, 3, 2, 1, 1, 1),
        "Party" to listOf(4, 4, 0, 0, 0, 0, 0, 0, 4, 4),
        "Reggae" to listOf(0, 0, 0, -3, 0, 4, 4, 0, 0, 0),
        "Ska" to listOf(-1, -2, -2, 0, 2, 3, 5, 6, 7, 6),
        "Soft" to listOf(3, 1, 0, -1, 0, 2, 5, 6, 7, 8),
        "Soft Rock" to listOf(2, 2, 1, 0, -2, -3, -1, 0, 1, 5),
        "Techno" to listOf(5, 3, 0, -3, -2, 0, 5, 6, 6, 5),
        "Vocal" to listOf(-1, -2, -2, 1, 4, 4, 3, 1, 0, -1),
        "Future Funk" to listOf(4, 3, 2, 1, -1, 2, 4, 5, 4, 3),
        "City Pop" to listOf(3, 2, 1, 0, 1, 3, 4, 3, 2, 1),
    )

    /** The preset whose gains match [gains] exactly, or null for a hand-tweaked custom curve. */
    fun presetFor(gains: List<Int>): String? =
        PRESETS.entries.firstOrNull { it.value == gains }?.key

    /**
     * The ten coefficient sets for [gains] at sample rate [fs]. Band 0 is a low shelf, band 9 a high
     * shelf, the rest peaking — the site's assignment. A zero-gain band designs to identity anyway,
     * so no special-casing is needed.
     */
    fun cascade(gains: List<Int>, fs: Double): List<BiquadCoeffs> =
        BANDS.mapIndexed { i, (freq, _) ->
            val g = gains.getOrElse(i) { 0 }.toDouble()
            when (i) {
                0 -> Biquad.lowShelf(freq.toDouble(), fs, g)
                BAND_COUNT - 1 -> Biquad.highShelf(freq.toDouble(), fs, g)
                else -> Biquad.peaking(freq.toDouble(), fs, g)
            }
        }

    /** A safe zeroed curve of the right length. */
    val ZERO_GAINS: List<Int> = List(BAND_COUNT) { 0 }
}
