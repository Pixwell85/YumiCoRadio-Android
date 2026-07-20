package net.yumicoradio.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

private fun DrawScope.clockGlyph() {
    val s = size.minDimension
    val stroke = Stroke(width = s * 0.09f)
    drawCircle(color = Win98.Ink, radius = s * 0.42f, style = stroke)
    val c = Offset(size.width / 2f, size.height / 2f)
    drawLine(Win98.Ink, c, Offset(c.x, c.y - s * 0.28f), strokeWidth = s * 0.09f)         // minute hand up
    drawLine(Win98.Ink, c, Offset(c.x + s * 0.20f, c.y), strokeWidth = s * 0.09f)          // hour hand right
}

private fun DrawScope.shareGlyph() {
    val s = size.minDimension
    val w = s * 0.09f
    val cx = size.width / 2f
    val top = size.height * 0.18f
    val mid = size.height * 0.52f
    // up arrow
    drawLine(Win98.Ink, Offset(cx, mid), Offset(cx, top), strokeWidth = w)
    drawLine(Win98.Ink, Offset(cx, top), Offset(cx - s * 0.16f, top + s * 0.16f), strokeWidth = w)
    drawLine(Win98.Ink, Offset(cx, top), Offset(cx + s * 0.16f, top + s * 0.16f), strokeWidth = w)
    // tray
    val left = size.width * 0.28f; val right = size.width * 0.72f; val bottom = size.height * 0.82f
    drawLine(Win98.Ink, Offset(left, mid), Offset(left, bottom), strokeWidth = w)
    drawLine(Win98.Ink, Offset(right, mid), Offset(right, bottom), strokeWidth = w)
    drawLine(Win98.Ink, Offset(left, bottom), Offset(right, bottom), strokeWidth = w)
}

private enum class Glyph { CLOCK, SHARE }

@Composable
private fun IconTextButton(
    glyph: Glyph,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier.background(Win98.Face).pressable(onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Canvas(Modifier.size(14.dp)) { if (glyph == Glyph.CLOCK) clockGlyph() else shareGlyph() }
        Spacer(Modifier.width(6.dp))
        Text(label, color = Win98.Ink, fontFamily = W95FA, fontSize = 11.sp)
    }
}

/**
 * The two app-only actions, as pixel-icon bevel buttons.
 *
 * They take a [modifier] because they share a deck with the transport buttons, and a row of
 * controls in three different sizes is exactly the improvised look this whole pass is undoing. The
 * caller gives all four the same width and height; the padding here is horizontal only so it cannot
 * fight the height it is given.
 */
@Composable
fun SleepButton(modifier: Modifier = Modifier, onClick: () -> Unit) =
    IconTextButton(Glyph.CLOCK, "Sleep", modifier, onClick)

@Composable
fun ShareButton(modifier: Modifier = Modifier, onClick: () -> Unit) =
    IconTextButton(Glyph.SHARE, "Share", modifier, onClick)
