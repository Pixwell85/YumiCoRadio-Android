package net.yumicoradio.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One window-chrome palette, derived from a single seed colour exactly as the website derives it.
 *
 * The site never writes its greys as literals: `applyWindowColors()` (`js/bgimg.js:711`) takes the
 * theme's `defaultWcPreset` and computes the whole family from it. Both of the app's palettes go
 * through [from] for that reason — quoting hex per theme is how the two colour regressions of
 * beta21 and beta22 happened, and a second hand-written palette would only double the chance.
 *
 * **The seed's luminance selects a branch**, and the dark one is not a recolour of the light one:
 * below 100 the site stops using white for the lit bevel ring and for the fill of sunken wells, and
 * derives both from the seed instead. A dark theme built by swapping greys but keeping white
 * highlights would glow along every bevel.
 */
class Win98Palette private constructor(
    val face: Color,
    val faceLit: Color,
    val highlight: Color,
    val shadow: Color,
    val darkEdge: Color,
    val borderMid: Color,
    val ink: Color,
    val inkDim: Color,
    val link: Color,
    val error: Color,
    val sunken: Color,
    val titlebarStops: List<Color>,
    val desktop: Color,
    val isDark: Boolean,
) {
    companion object {
        /** `js/bgimg.js:352` — clamp, round, pack. */
        private fun rgb(r: Double, g: Double, b: Double): Color {
            fun ch(c: Double) = min(255.0, max(0.0, c)).roundToInt()
            return Color(ch(r), ch(g), ch(b))
        }

        /** `c * factor` — the site darkens by scaling toward black. */
        private fun Color.scaled(factor: Double) =
            rgb(red * 255.0 * factor, green * 255.0 * factor, blue * 255.0 * factor)

        /** `c + (255 - c) * amount` — and lightens toward white this way. */
        private fun Color.lightened(amount: Double) = rgb(
            red * 255.0 + (255 - red * 255.0) * amount,
            green * 255.0 + (255 - green * 255.0) * amount,
            blue * 255.0 + (255 - blue * 255.0) * amount,
        )

        /** The site's own luminance, on a 0–255 scale. */
        fun luminance(seed: Color): Double =
            seed.red * 255.0 * 0.299 + seed.green * 255.0 * 0.587 + seed.blue * 255.0 * 0.114

        fun from(
            seed: Color,
            ink: Color,
            inkDim: Color,
            link: Color,
            error: Color,
            titlebarStops: List<Color>,
            desktop: Color,
        ): Win98Palette {
            val isDark = luminance(seed) < 100
            return Win98Palette(
                face = seed,
                faceLit = seed.lightened(0.13),
                // The dark branch: `--button-highlight` and `--window` come off the seed rather
                // than being white.
                highlight = if (isDark) seed.lightened(0.2) else Color.White,
                shadow = seed.scaled(0.67),
                darkEdge = seed.scaled(0.05),
                borderMid = seed.scaled(0.93),
                ink = ink,
                inkDim = inkDim,
                link = link,
                error = error,
                sunken = if (isDark) seed.scaled(0.5) else Color.White,
                titlebarStops = titlebarStops,
                desktop = desktop,
                isDark = isDark,
            )
        }

        /**
         * `theme19` "Star OS 99" — what `js/bgimg.js:1871` loads for a visitor with no stored
         * theme, and so the app's default.
         */
        val Light = from(
            seed = Color(0xFFD4D0C8),
            ink = Color(0xFF000000),
            inkDim = Color(0xFF55525A),
            link = Color(0xFF0000CC),
            error = Color(0xFF7A0000),
            titlebarStops = listOf(Color(0xFF0A246A), Color(0xFFA6CAF0)),
            desktop = Color(0xFF3A6EA5),
        )

        /**
         * `theme22` "Star OS 99 Dark" (`js/themes.js:1957`) — the site's own dark counterpart, not
         * an inversion. Its title bar gains a third stop at black on the left, and the desktop goes
         * to a near-black navy.
         */
        val Dark = from(
            seed = Color(0xFF2D2D2D),
            ink = Color(0xFFE0E0E0),
            // Secondary text cannot follow `shadow` in the dark theme the way it reads as a grey in
            // the light one: `shadow` is the seed at 67%, which on a #2d2d2d face is darker than the
            // face itself. It has to come back up toward the ink instead.
            inkDim = Color(0xFF9A9A9A),
            // `--about-link-color` in theme22. The light theme's navy would sit invisible here.
            link = Color(0xFFA6CAF0),
            error = Color(0xFFFF6B6B),
            titlebarStops = listOf(Color(0xFF000000), Color(0xFF0A246A), Color(0xFFA6CAF0)),
            desktop = Color(0xFF020C1A),
        )
    }
}
