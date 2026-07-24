// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The single source of the app's Win9x look, matching the website's **default theme**.
 *
 * That theme is `theme19` "Star OS 99" in `js/themes.js` — `js/bgimg.js:1871` selects it when no
 * theme is stored, which is what a first-time visitor sees. It is a Windows 2000 look, not a
 * Windows 98 one.
 *
 * **The greys are computed, not quoted.** The site never writes `--surface` and friends as
 * literals: `applyWindowColors()` (`js/bgimg.js:711`) takes one seed colour — the theme's
 * `defaultWcPreset`, here `win2000` = `#d4d0c8` — and derives the whole family from it:
 *
 * ```
 * button-face   = c + (255 - c) * 0.13
 * button-shadow = c * 0.67
 * window-frame  = c * 0.05
 * highlight     = #ffffff        (the seed is light: luminance 208)
 * border-mid    = c * 0.93
 * ```
 *
 * The values below are that arithmetic evaluated for `#d4d0c8`. They are deliberately *not*
 * 98.css's `#c0c0c0` family: 98.css is the stylesheet the site is built on, but the running site
 * overrides it, and beta22 shipped the library's palette instead of the page's.
 *
 * Components must draw from this file rather than carry their own numbers. Scattered,
 * slightly-off values are what produced the "windows 9x bricolé et incohérent" verdict on beta21.
 */
object Win98 {
    /**
     * The palette in force. Snapshot state on purpose: every colour below reads through it, so a
     * theme change invalidates each composable *and each draw scope* that touched a colour, and
     * the whole app repaints without a single call site knowing a theme system exists.
     *
     * That is why these are getters rather than the `val`s they used to be. The bevel modifiers in
     * `Win98.kt` are not composable — they read colours inside `drawBehind` — so a
     * `CompositionLocal` could not have reached them without rewriting every bevel in the app.
     */
    var palette: Win98Palette by mutableStateOf(Win98Palette.Light)
        private set

    fun setDark(dark: Boolean) {
        palette = if (dark) Win98Palette.Dark else Win98Palette.Light
    }

    // ── Derived from the active theme's seed ────────────────────────────────────────────────────
    /** `--surface`: the seed itself. The face of windows, buttons and bars. */
    val Face: Color get() = palette.face

    /** `--button-face`, seed lightened 13% toward white: the inner lit bevel ring. */
    val FaceLit: Color get() = palette.faceLit

    /** `--button-highlight`: the outer lit bevel ring. White only while the seed is light. */
    val Highlight: Color get() = palette.highlight

    /** `--button-shadow`, seed at 67%: the inner dark ring, and disabled text. Warm, not neutral. */
    val Shadow: Color get() = palette.shadow

    /** `--window-frame`, seed at 5%: the outer dark ring. Near-black, not black. */
    val DarkEdge: Color get() = palette.darkEdge

    /** `--window-border-mid`, seed at 93%. */
    val BorderMid: Color get() = palette.borderMid

    /** `--window-text-color`: the active preset's text colour. */
    val Ink: Color get() = palette.ink

    /**
     * Secondary text: artists under a title, timestamps, a dimmed menu entry.
     *
     * Not [Shadow], which is what these used before. Shadow is the seed at 67% — a readable grey
     * under a light seed, but *darker than the face* under a dark one, so every dimmed label would
     * vanish the moment the theme flipped.
     */
    val InkDim: Color get() = palette.inkDim

    /** Hyperlinks. Navy on the light theme, the pale blue accent on the dark one. */
    val Link: Color get() = palette.link

    /** Error text, e.g. a refused nickname. */
    val Error: Color get() = palette.error

    /** `--window`: the fill of a sunken well — a text field, a list, an artwork frame. */
    val Sunken: Color get() = palette.sunken

    /** 98.css `--dialog-blue` / `--dialog-blue-light`: selection and progress blocks. */
    val DialogBlue = Color(0xFF000080)
    val DialogBlueLight = Color(0xFF1084D0)

    // ── Per-theme chrome ────────────────────────────────────────────────────────────────────────
    /** `--titlebar-gradient`, left to right. Two stops light, three dark. */
    val TitlebarStops: List<Color> get() = palette.titlebarStops
    val TitlebarBrush: Brush get() = Brush.horizontalGradient(palette.titlebarStops)

    /** `--titlebar-text-color`. White in both themes. */
    val TitlebarText = Color(0xFFFFFFFF)

    /** `--body-background`: a flat desktop colour, stated as a gradient of one colour. */
    val Desktop: Color get() = palette.desktop
    val DesktopBrush: Brush get() = Brush.verticalGradient(listOf(palette.desktop, palette.desktop))

    /** `--song-title-color`: the pale blue accent. theme19 and theme22 agree on it. */
    val SongTitle = Color(0xFFA6CAF0)

    /** Kept for the "LIVE" indicator and the accents the player already uses. */
    val LiveGreen = Color(0xFF067306)
    val Accent = Color(0xFFD4007A)
}

/**
 * Win9x metrics, in the units 98.css states them in. One CSS pixel maps to one dp: both are
 * density-independent, so a rule written for a 96dpi desktop lands at the same visual size here.
 */
object Win98Metrics {
    /**
     * Bevel ring thickness. 98.css draws two 1px rings (`inset 1px` then `inset 2px`), so one dp
     * per ring is the faithful value — beta21 used two, which doubled every edge in the app and read
     * as homemade.
     */
    val Bevel = 1.dp

    /** `.window` padding. */
    val WindowPadding = 3.dp

    /**
     * `.title-bar` padding is 3px vertical on the desktop. Two dp here: the bar was reading tall on
     * a phone, where it competes with the system status bar directly above it.
     */
    val TitleBarPaddingV = 2.dp
    val TitleBarPaddingH = 3.dp

    /** Title-bar icon. One dp under the button height, so it never sets the bar's height. */
    val TitleBarIcon = 13.dp

    /** `.window-body` margin — `--element-spacing`. */
    val ElementSpacing = 8.dp

    /** `--grouped-element-spacing` and `--grouped-button-spacing`. */
    val GroupedSpacing = 6.dp
    val GroupedButtonSpacing = 4.dp

    /** `button` metrics: `min-width: 75px; min-height: 23px; padding: 0 12px`. */
    val ButtonMinWidth = 75.dp
    val ButtonMinHeight = 23.dp
    val ButtonPaddingH = 12.dp

    /**
     * `.title-bar-controls button` is `16x14px` on the desktop — very slightly oblong, which reads
     * as a squashed button at phone size. Square here, and kept small: the previous 24x21 was what
     * made the bar bulky.
     *
     * This is under Android's 48dp touch guidance, deliberately: a title bar sized for a thumb stops
     * being a Win9x title bar. The buttons are corner targets used rarely, and every one of them has
     * another route (system back closes, the menu bar navigates). [TitleBarButtonGap] buys back some
     * of the accuracy instead — separation matters more than size for hitting the right one.
     */
    val TitleBarButtonSize = 18.dp

    /** Space between the title bar controls, and between the title and the first of them. */
    val TitleBarButtonGap = 6.dp

    /** `--checkbox-width` and `--radio-width`. */
    val CheckboxSize = 13.dp
    val RadioSize = 12.dp

    /** `--radio-label-spacing`. */
    val LabelSpacing = 6.dp

    /** `.status-bar-field` padding. */
    val StatusFieldPaddingH = 3.dp
    val StatusFieldPaddingV = 2.dp
}

/**
 * The type scale. 98.css sets chrome at 11px and the website repeats that in Tahoma
 * (`css/default.css:26`). Android has no Tahoma, so the app's bundled W95FA — a Win9x UI face —
 * carries the whole chrome.
 *
 * Four sizes, all of them justified. Screens picking their own is how the app ended up with 9, 10,
 * 11 and 13 sp used interchangeably for the same kind of text.
 */
object Win98Type {
    /** Chrome default: labels, buttons, list rows, title bars. The website's 11px. */
    val Body = 11.sp

    /** Secondary text: hints under a checkbox, timestamps, status fields. */
    val Small = 9.sp

    /** Section headings inside a window body. */
    val Heading = 13.sp

    /** Line height for wrapped paragraphs at [Body]. */
    val BodyLineHeight = 15.sp
}

/**
 * [dark] is the user's own choice, not the system's. The website treats dark as a *theme* you pick
 * — "Star OS 99 Dark" sits in the same list as every other — so following Android's system setting
 * would put the app's appearance somewhere the site never puts it.
 */
@Composable
fun Win98Theme(dark: Boolean = false, content: @Composable () -> Unit) {
    // Applied during composition rather than in a side effect: the colours below are read in the
    // same pass, so a deferred write would paint one frame with the outgoing palette.
    Win98.setDark(dark)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Win98.DialogBlue,
            background = Win98.Face,
            surface = Win98.Face,
            onSurface = Win98.Ink,
        ),
        content = content,
    )
}
