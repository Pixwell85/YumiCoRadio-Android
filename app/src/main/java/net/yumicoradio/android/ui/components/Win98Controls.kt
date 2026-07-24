// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

/**
 * A Win9x group box: a sunken hairline frame with its title straddling the top edge.
 *
 * This is how the website groups its settings, and it is what makes a long options screen readable
 * without inventing headings of a different species.
 */
@Composable
fun Win98Fieldset(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth().padding(top = 7.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .drawBehind {
                    val t = 1.dp.toPx()
                    // Two tones, one px apart: the etched frame Win9x uses for group boxes.
                    drawRect(Win98.Shadow, Offset(0f, 0f), Size(size.width - t, t))
                    drawRect(Win98.Shadow, Offset(0f, 0f), Size(t, size.height - t))
                    drawRect(Win98.Highlight, Offset(t, size.height - t), Size(size.width - t, t))
                    drawRect(Win98.Highlight, Offset(size.width - t, t), Size(t, size.height - t))
                }
                .padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp),
            content = content,
        )
        // The title sits on the frame, its background punching a hole in the line behind it.
        Text(
            title,
            fontFamily = W95FA, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Win98.Ink,
            modifier = Modifier.offset(x = 8.dp, y = (-7).dp)
                .background(Win98.Face)
                .padding(horizontal = 4.dp),
        )
    }
}

/**
 * The tick, drawn rather than typed.
 *
 * `Text("✔")` put it low in the box: a glyph rests on the font's baseline, and centring the *text
 * box* does not centre the ink inside it — the same defect the title bar's close cross had, and for
 * the same reason. It also left the mark at the mercy of whichever font the device substituted for
 * a character W95FA does not carry.
 *
 * Two strokes, the short arm down-right and the long arm up-right, proportional to the box.
 */
private fun DrawScope.drawCheckmark(ink: Color) {
    val w = size.width
    val h = size.height
    val stroke = (w / 7f).coerceAtLeast(1.5f)
    val left = Offset(w * 0.22f, h * 0.52f)
    val bottom = Offset(w * 0.42f, h * 0.74f)
    val right = Offset(w * 0.78f, h * 0.28f)
    drawLine(ink, left, bottom, stroke, cap = StrokeCap.Square)
    drawLine(ink, bottom, right, stroke, cap = StrokeCap.Square)
}

/** A Win9x checkbox: a sunken well with a drawn tick, and its label to the right. */
@Composable
fun Win98Checkbox(
    checked: Boolean,
    label: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(modifier.fillMaxWidth().tappable { onToggle(!checked) }.padding(vertical = 5.dp)) {
        // The box and its label share one centred row; the description hangs underneath, indented
        // to the label. Putting both texts in a column beside the box left the label riding low.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(14.dp).background(Win98.Sunken).sunken()
                    .drawBehind { if (checked) drawCheckmark(Win98.Ink) },
            )
            Spacer(Modifier.width(7.dp))
            Text(label, fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
        }
        description?.let {
            Text(
                it,
                fontFamily = W95FA, fontSize = 9.sp, color = Win98.InkDim, lineHeight = 12.sp,
                modifier = Modifier.padding(start = 21.dp, top = 1.dp),
            )
        }
    }
}

/** A Win9x radio button: a sunken round well with a filled dot when chosen. */
@Composable
fun Win98Radio(
    selected: Boolean,
    label: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().tappable(onSelect).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(13.dp).clip(CircleShape).background(Win98.Sunken)
                .drawBehind {
                    val t = 1.dp.toPx()
                    // Shadow on the top-left arc, highlight on the bottom-right: the round well.
                    drawArc(Win98.Shadow, 135f, 180f, false, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(t))
                    drawArc(Win98.Highlight, 315f, 180f, false, size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(t))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(Win98.Ink))
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(label, fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
    }
}

/** A determinate Win9x progress bar: chunky blue blocks in a sunken well. */
@Composable
fun Win98ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(14.dp).background(Win98.Sunken).sunken().padding(2.dp),
    ) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                // Discrete blocks rather than a smooth fill: this is the Win9x idiom, and it also
                // makes slow progress visible as it steps.
                val blockWidth = 8.dp.toPx()
                val gap = 2.dp.toPx()
                val filled = (size.width * fraction.coerceIn(0f, 1f))
                var x = 0f
                while (x + blockWidth <= filled) {
                    drawRect(Win98.DialogBlue, Offset(x, 0f), Size(blockWidth, size.height))
                    x += blockWidth + gap
                }
            },
        )
    }
}
