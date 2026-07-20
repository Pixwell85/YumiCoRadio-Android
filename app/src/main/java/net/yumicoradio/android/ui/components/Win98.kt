package net.yumicoradio.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Metrics
import net.yumicoradio.android.ui.theme.Win98Type

/**
 * The four-layer Win9x bevel: an outer ring and an inner ring, each [Win98Metrics.Bevel] thick, lit
 * from opposite directions. This is 98.css's `inset 1px` / `inset 2px` box-shadow pair.
 *
 * Drawn as filled rects rather than lines: `drawLine` centres its stroke on the path, so an edge
 * line at offset 0 bleeds half outside the bounds. Rects land exactly where they are told.
 */
private fun DrawScope.bevel(
    outerTopLeft: Color,
    outerBottomRight: Color,
    innerTopLeft: Color,
    innerBottomRight: Color,
) {
    val t = Win98Metrics.Bevel.toPx()
    val w = size.width
    val h = size.height

    drawRect(outerTopLeft, Offset(0f, 0f), Size(w, t))
    drawRect(outerTopLeft, Offset(0f, 0f), Size(t, h))
    drawRect(outerBottomRight, Offset(0f, h - t), Size(w, t))
    drawRect(outerBottomRight, Offset(w - t, 0f), Size(t, h))

    drawRect(innerTopLeft, Offset(t, t), Size(w - 2 * t, t))
    drawRect(innerTopLeft, Offset(t, t), Size(t, h - 2 * t))
    drawRect(innerBottomRight, Offset(t, h - 2 * t), Size(w - 2 * t, t))
    drawRect(innerBottomRight, Offset(w - 2 * t, t), Size(t, h - 2 * t))
}

/** Raised: lit from the top-left. Buttons and bars — `--border-raised-outer` + `-inner`. */
fun Modifier.raised(): Modifier = drawBehind {
    bevel(Win98.Highlight, Win98.DarkEdge, Win98.FaceLit, Win98.Shadow)
}

/**
 * A window's frame. 98.css calls this out explicitly: window borders *flip* button-face and
 * button-highlight against the button bevel, so the outer lit ring is the softer grey and the
 * inner one is white. Using the button bevel on windows — as the app did through beta21 — makes
 * every window read as a giant button, which is half of what "bricolé" was describing.
 */
fun Modifier.windowFrame(): Modifier = drawBehind {
    bevel(Win98.FaceLit, Win98.DarkEdge, Win98.Highlight, Win98.Shadow)
}

/**
 * Deep sunken: the raised bevel inverted. For wells that hold content — artwork, lists.
 * This is the window-body depth, not the status-bar field depth.
 */
fun Modifier.sunkenDeep(): Modifier = drawBehind {
    bevel(Win98.DarkEdge, Win98.Highlight, Win98.Shadow, Win98.FaceLit)
}

/**
 * Shallow sunken — 98.css's `--border-status-field`: one ring, shadow above-left and face
 * below-right. The faintest inset in the idiom, and deliberately shallower than a window body: it
 * separates status panes without digging a well.
 */
fun Modifier.sunken(): Modifier = drawBehind {
    val t = Win98Metrics.Bevel.toPx()
    val w = size.width
    val h = size.height
    drawRect(Win98.Shadow, Offset(0f, 0f), Size(w, t))
    drawRect(Win98.Shadow, Offset(0f, 0f), Size(t, h))
    drawRect(Win98.FaceLit, Offset(0f, h - t), Size(w, t))
    drawRect(Win98.FaceLit, Offset(w - t, 0f), Size(t, h))
}

/**
 * A Win9x button's press: the bevel inverts and the face sinks one dp toward the bottom-right.
 *
 * The offset is not decoration. A bevel swap alone barely registers on a small button; moving the
 * content with it is what makes the press read as a press.
 *
 * `indication = null` removes Material's ripple, which has no place in this UI.
 *
 * Owns remembered state, so it must be called unconditionally — never behind an `if`, which would
 * shift its slot in the composition.
 */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    return this
        .then(if (pressed) Modifier.sunkenDeep() else Modifier.raised())
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .offset(x = if (pressed) 1.dp else 0.dp, y = if (pressed) 1.dp else 0.dp)
}

/**
 * A tap target with no button chrome at all: no bevel, no ripple, nothing drawn.
 *
 * For links, list rows and the label beside a checkbox — things you can press that are not buttons.
 * Reaching for [pressable] there is what put a raised button edge around every stream link, every
 * settings row and every name in the user list.
 *
 * Owns remembered state, so like [pressable] it must be called unconditionally.
 */
@Composable
fun Modifier.tappable(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

/**
 * Title bar height is fixed on purpose — a Win9x title bar never grows to fit controls, which is
 * why navigation lives in the tab bar rather than here.
 */
@Composable
fun Win98Window(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onMinimize: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    menuBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.background(Win98.Face).windowFrame().padding(Win98Metrics.WindowPadding)) {
        Row(
            Modifier.fillMaxWidth()
                .background(Win98.TitlebarBrush)
                .padding(
                    horizontal = Win98Metrics.TitleBarPaddingH,
                    vertical = Win98Metrics.TitleBarPaddingV,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                // The site's own icons (Star OS 99 set, `js/iconsets.js`), not emoji: a title bar
                // rendering 📻 in the system emoji font is the loudest tell that the chrome is
                // improvised. 16dp is the Win9x title-bar icon size.
                Image(
                    painter = painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(Win98Metrics.TitleBarIcon),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                title, color = Win98.TitlebarText, fontFamily = W95FA, fontWeight = FontWeight.Bold,
                fontSize = Win98Type.Body,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            // The title claims the free width, so the gap before the controls is guaranteed even
            // when the title is long enough to ellipsise.
            Spacer(Modifier.width(Win98Metrics.TitleBarButtonGap))
            if (onMinimize != null) {
                TitleBarButton(TitleGlyph.Minimize, onMinimize)
                Spacer(Modifier.width(Win98Metrics.TitleBarButtonGap))
            }
            if (onClose != null) TitleBarButton(TitleGlyph.Close, onClose)
        }
        // The menu bar sits outside the body padding on purpose: in a real window it runs flush
        // under the title bar, edge to edge. Inset by 8dp it reads as a control floating on the
        // face rather than as part of the frame.
        menuBar?.invoke()
        Column(Modifier.padding(8.dp), content = content)
    }
}

/**
 * The two title-bar glyphs, drawn rather than typed.
 *
 * Typing them put the close cross low in its button: a glyph sits on the font's baseline, and
 * centring the *text box* does not centre the ink inside it. Windows does not use font glyphs here
 * either — 98.css loads an SVG per control.
 *
 * The minimize bar is deliberately not centred: it belongs near the bottom, which is where 98.css
 * places it (`background-position: bottom 3px`) and what makes it read as "minimize" rather than
 * "dash".
 */
private enum class TitleGlyph { Minimize, Close }

/** Sized from [Win98Metrics.TitleBarButtonSize] — see the note there on the touch-target call. */
@Composable
private fun TitleBarButton(glyph: TitleGlyph, onClick: () -> Unit) {
    Box(
        Modifier.size(Win98Metrics.TitleBarButtonSize)
            .background(Win98.Face).pressable(onClick)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                val ink = Win98.Ink
                when (glyph) {
                    TitleGlyph.Minimize -> {
                        val w = 7.dp.toPx()
                        drawRect(
                            ink,
                            Offset((size.width - w) / 2f, size.height - 4.dp.toPx()),
                            Size(w, stroke),
                        )
                    }
                    TitleGlyph.Close -> {
                        val arm = 6.dp.toPx()
                        val x0 = (size.width - arm) / 2f
                        val y0 = (size.height - arm) / 2f
                        drawLine(ink, Offset(x0, y0), Offset(x0 + arm, y0 + arm), stroke)
                        drawLine(ink, Offset(x0 + arm, y0), Offset(x0, y0 + arm), stroke)
                    }
                }
            },
    )
}

/**
 * `button` in 98.css: 75x23px minimum, 12px of horizontal padding, 11px type.
 *
 * [big] is the player's transport row, which is sized for a thumb rather than a mouse.
 *
 * A disabled button keeps its raised face and greys its label to `--button-shadow`, as Win9x does —
 * it stays visible so the reader can see what will become available, but swallows the tap. The
 * bevel must still be built unconditionally: [pressable] owns remembered state, so branching around
 * it would move its slot in the composition.
 */
@Composable
fun Win98Button(
    label: String,
    big: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .then(
                if (big) Modifier
                else Modifier.defaultMinSize(Win98Metrics.ButtonMinWidth, Win98Metrics.ButtonMinHeight)
            )
            .background(Win98.Face).pressable { if (enabled) onClick() }
            .padding(
                horizontal = if (big) 18.dp else Win98Metrics.ButtonPaddingH,
                vertical = if (big) 14.dp else 4.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontFamily = W95FA, fontSize = if (big) 20.sp else Win98Type.Body,
            color = if (enabled) Win98.Ink else Win98.Shadow,
        )
    }
}
