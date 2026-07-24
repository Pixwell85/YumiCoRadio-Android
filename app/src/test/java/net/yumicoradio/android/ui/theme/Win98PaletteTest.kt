// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the design system to the website's default theme.
 *
 * Two ways of getting this wrong have already shipped. beta21 drifted by eye — `#e8e6e0` where the
 * site computes `#dad6cf`. beta22 then "corrected" the palette to 98.css's `#c0c0c0` family, which
 * is the stylesheet the site is *built on* but not what it *renders*: `js/bgimg.js:1871` loads
 * `theme19` "Star OS 99" for anyone without a stored theme.
 *
 * So this test does not quote hex. It re-runs the site's own derivation — `applyWindowColors()` in
 * `js/bgimg.js:711` — from the same seed and checks the constants match. If the site changes its
 * seed or its arithmetic, these fail and point at the formula rather than at a colour picker.
 */
class Win98PaletteTest {

    /**
     * The active palette is process-wide state, so a test that switches it would otherwise leave
     * every later test reading the wrong theme — and which tests those are depends on run order.
     */
    @After
    fun restoreLightTheme() {
        Win98.setDark(false)
    }

    /** `windowColorPresets['win2000']`, the `defaultWcPreset` of theme19. */
    private val seed = Triple(0xD4, 0xD0, 0xC8)

    /** `rgbToHex` in `js/bgimg.js:352`: clamp, round, pack. */
    private fun rgb(r: Double, g: Double, b: Double): Color {
        fun ch(c: Double) = min(255.0, max(0.0, c)).roundToInt()
        return Color(ch(r), ch(g), ch(b))
    }

    private fun derive(factor: Double) =
        rgb(seed.first * factor, seed.second * factor, seed.third * factor)

    /** `c + (255 - c) * amount` — the site lightens toward white this way. */
    private fun lighten(amount: Double) = rgb(
        seed.first + (255 - seed.first) * amount,
        seed.second + (255 - seed.second) * amount,
        seed.third + (255 - seed.third) * amount,
    )

    @Test
    fun `surface is the win2000 seed`() {
        assertEquals(Color(seed.first, seed.second, seed.third), Win98.Face)
    }

    @Test
    fun `greys follow the site's derivation from the seed`() {
        assertEquals(lighten(0.13), Win98.FaceLit, "--button-face")
        assertEquals(derive(0.67), Win98.Shadow, "--button-shadow")
        assertEquals(derive(0.05), Win98.DarkEdge, "--window-frame")
        assertEquals(derive(0.93), Win98.BorderMid, "--window-border-mid")
    }

    /**
     * The seed's luminance decides two colours. At 208 it is light, so the site uses white for both
     * the lit bevel ring and the fill of sunken wells — a dark seed would take the other branch.
     */
    @Test
    fun `a light seed gives white highlights and white wells`() {
        val luminance =
            seed.first * 0.299 + seed.second * 0.587 + seed.third * 0.114
        assert(luminance >= 100) { "seed luminance $luminance would take the dark branch" }
        assertEquals(Color.White, Win98.Highlight)
        assertEquals(Color.White, Win98.Sunken)
    }

    /**
     * From theme19's own variables. Windows 2000 blue — deliberately not 98.css's navy, and not
     * theme1's four-stop rainbow, which is what beta22 shipped by mistaking the stylesheet's
     * `:root` defaults for the running theme.
     */
    @Test
    fun `title bar and desktop come from theme19`() {
        assertEquals(
            listOf(Color(0xFF0A246A), Color(0xFFA6CAF0)), Win98.TitlebarStops, "--titlebar-gradient",
        )
        assertEquals(Color(0xFFFFFFFF), Win98.TitlebarText, "--titlebar-text-color")
        assertEquals(Color(0xFF3A6EA5), Win98.Desktop, "--body-background")
        assertEquals(Color(0xFF000000), Win98.Ink, "--window-text-color")
    }

    /** `windowColorPresets['dark']` (`js/bgimg.js:707`), the `defaultWcPreset` of theme22. */
    private val darkSeed = Triple(0x2D, 0x2D, 0x2D)

    private fun darkDerive(factor: Double) =
        rgb(darkSeed.first * factor, darkSeed.second * factor, darkSeed.third * factor)

    private fun darkLighten(amount: Double) = rgb(
        darkSeed.first + (255 - darkSeed.first) * amount,
        darkSeed.second + (255 - darkSeed.second) * amount,
        darkSeed.third + (255 - darkSeed.third) * amount,
    )

    /**
     * The branch that makes a dark theme more than a grey swap.
     *
     * Below luminance 100 the site stops using white for `--button-highlight` and `--window` and
     * derives both from the seed. Keeping white there — the obvious way to build a dark palette —
     * would leave every bevel and every text field glowing.
     */
    @Test
    fun `the dark seed takes the site's dark branch`() {
        val luminance =
            darkSeed.first * 0.299 + darkSeed.second * 0.587 + darkSeed.third * 0.114
        assert(luminance < 100) { "seed luminance $luminance would take the light branch" }

        val dark = Win98Palette.Dark
        assertEquals(darkLighten(0.2), dark.highlight, "--button-highlight, dark branch")
        assertEquals(darkDerive(0.5), dark.sunken, "--window, dark branch")
        assert(dark.highlight != Color.White) { "the dark branch must not keep a white highlight" }
        assert(dark.sunken != Color.White) { "the dark branch must not keep white wells" }
    }

    @Test
    fun `dark greys follow the same derivation as the light ones`() {
        val dark = Win98Palette.Dark
        assertEquals(Color(darkSeed.first, darkSeed.second, darkSeed.third), dark.face, "--surface")
        assertEquals(darkLighten(0.13), dark.faceLit, "--button-face")
        assertEquals(darkDerive(0.67), dark.shadow, "--button-shadow")
        assertEquals(darkDerive(0.05), dark.darkEdge, "--window-frame")
        assertEquals(darkDerive(0.93), dark.borderMid, "--window-border-mid")
    }

    /** theme22's own variables — including the third title-bar stop theme19 does not have. */
    @Test
    fun `dark chrome comes from theme22`() {
        val dark = Win98Palette.Dark
        assertEquals(Color(0xFFE0E0E0), dark.ink, "the dark preset's text colour")
        assertEquals(
            listOf(Color(0xFF000000), Color(0xFF0A246A), Color(0xFFA6CAF0)),
            dark.titlebarStops,
            "--titlebar-gradient",
        )
        assertEquals(Color(0xFF020C1A), dark.desktop, "--body-background")
    }

    /**
     * The tokens that exist precisely because they cannot be derived.
     *
     * Secondary text used to be `Win98.Shadow`. That is the seed at 67% — fine on a light face,
     * but on `#2d2d2d` it lands *darker than the face*, so every artist line, timestamp and dimmed
     * menu entry would have disappeared. Same for the navy link colour on a near-black window.
     * These assertions are about legibility, not about matching a formula.
     */
    @Test
    fun `secondary text and links stay legible on the dark face`() {
        val dark = Win98Palette.Dark
        fun lum(c: Color) = Win98Palette.luminance(c)

        assert(lum(dark.inkDim) > lum(dark.face) + 40) {
            "secondary text (${lum(dark.inkDim)}) must sit clear of the face (${lum(dark.face)})"
        }
        assert(lum(dark.inkDim) < lum(dark.ink)) { "secondary text must stay dimmer than body text" }
        assert(lum(dark.link) > lum(dark.face) + 40) { "links must be legible on the dark face" }
        assert(lum(dark.error) > lum(dark.face) + 40) { "error text must be legible on the dark face" }

        // And the light theme keeps exactly the values the screens shipped with.
        assertEquals(Color(0xFF55525A), Win98Palette.Light.inkDim)
        assertEquals(Color(0xFF0000CC), Win98Palette.Light.link)
        assertEquals(Color(0xFF7A0000), Win98Palette.Light.error)
    }

    /** Switching themes must actually move the object every component reads through. */
    @Test
    fun `setDark swaps the active palette and back`() {
        Win98.setDark(true)
        assertEquals(Win98Palette.Dark.face, Win98.Face)
        assertEquals(Win98Palette.Dark.ink, Win98.Ink)
        Win98.setDark(false)
        assertEquals(Win98Palette.Light.face, Win98.Face)
        assertEquals(Color.White, Win98.Highlight)
    }

    /**
     * Metrics still come from 98.css: the site overrides colours per theme but keeps the
     * stylesheet's geometry. Two 1px bevel rings, and `button { min-width: 75px; min-height: 23px;
     * padding: 0 12px }` at 11px type.
     */
    @Test
    fun `metrics and type scale match 98 css`() {
        assertEquals(1f, Win98Metrics.Bevel.value)
        assertEquals(75f, Win98Metrics.ButtonMinWidth.value)
        assertEquals(23f, Win98Metrics.ButtonMinHeight.value)
        assertEquals(12f, Win98Metrics.ButtonPaddingH.value)
        assertEquals(11f, Win98Type.Body.value)
    }
}
