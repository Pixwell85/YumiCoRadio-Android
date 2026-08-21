// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Pure logic for the volume meter, split out so it is JVM-testable without Compose.
 * Ten segments, tiered 6 green / 2 orange / 2 red — matching the site's led-volume-display.
 */
object VolumeMeter {
    const val SEGMENTS = 10

    enum class Tier { GREEN, ORANGE, RED }

    /** How many of the [SEGMENTS] segments light up for a 0.0–1.0 volume. */
    fun litSegments(volume: Float): Int = (volume.coerceIn(0f, 1f) * SEGMENTS).roundToInt()

    /** Colour tier of the segment at [index] (0-based). */
    fun tier(index: Int): Tier = when {
        index <= 5 -> Tier.GREEN
        index <= 7 -> Tier.ORANGE
        else -> Tier.RED
    }
}

// The hi-fi meter's fill: a single green→amber→red wash mapped across the *whole* bar, so a segment's
// colour depends on where it sits, not how loud it is. Stops nudged off pure RGB for a warmer read.
private val VmFillStops = arrayOf(
    0.00f to Color(0xFF0BD85A),
    0.42f to Color(0xFF39FF6A),
    0.60f to Color(0xFFFFD23A),
    0.72f to Color(0xFFFFB020),
    0.84f to Color(0xFFFF6A3A),
    1.00f to Color(0xFFFF4438),
)

private val VmLcdBg = Color(0xFF0C0D0A)
private val VmBevelDark = Color(0xFF000000)
private val VmBevelLight = Color(0x22FFFFFF)
private val VmEtchLine = Color(0xA6000000)
private val VmGloss = Color(0x33FFFFFF)
private val VmGlossOut = Color(0x00FFFFFF)

/**
 * The volume control as a hi-fi VU bar: one continuous green→amber→red fill in a recessed black LCD
 * trough, its width the volume, with ten faint etched dividers over it. Drag or tap anywhere sets
 * [volume] (0.0–1.0) through [onVolume]. 24dp tall for a comfortable touch target.
 */
@Composable
fun VolumeMeterBar(
    volume: Float,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
) {
    var widthPx by remember { mutableStateOf(1) }
    val v = volume.coerceIn(0f, 1f)
    Box(
        modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onInteractionStart()
                        tryAwaitRelease()
                        onInteractionEnd()
                    },
                    onTap = { pos -> onVolume((pos.x / widthPx).coerceIn(0f, 1f)) },
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { onInteractionStart() },
                    onDragEnd = onInteractionEnd,
                    onDragCancel = onInteractionEnd,
                ) { change, _ -> onVolume((change.position.x / widthPx).coerceIn(0f, 1f)) }
            }
            .drawBehind { drawHiFiBar(v) },
    )
}

private fun DrawScope.drawHiFiBar(v: Float) {
    val w = size.width
    val h = size.height
    val t = 1.dp.toPx()
    val pad = 3.dp.toPx()

    // Recessed trough: dark ground with an inset bevel — dark top/left, faint light bottom/right.
    drawRect(VmLcdBg)
    drawRect(VmBevelDark, Offset(0f, 0f), Size(w, t))
    drawRect(VmBevelDark, Offset(0f, 0f), Size(t, h))
    drawRect(VmBevelLight, Offset(0f, h - t), Size(w, t))
    drawRect(VmBevelLight, Offset(w - t, 0f), Size(t, h))

    val fx = pad
    val fy = pad
    val fw = (w - pad * 2).coerceAtLeast(0f)
    val fh = (h - pad * 2).coerceAtLeast(0f)
    val fillW = fw * v

    if (fillW > 0f && fh > 0f) {
        // The gradient spans the full inner width; the fill rect just reveals part of it, so a
        // segment keeps its colour as the level rises past it.
        val brush = Brush.horizontalGradient(*VmFillStops, startX = fx, endX = fx + fw)
        drawRect(brush, topLeft = Offset(fx, fy), size = Size(fillW, fh))
        // A gloss down the top half sells the glass.
        val gloss = Brush.verticalGradient(0f to VmGloss, 0.5f to VmGlossOut, startY = fy, endY = fy + fh)
        drawRect(gloss, topLeft = Offset(fx, fy), size = Size(fillW, fh))
    }

    // Ten etched dividers, drawn over the whole inner area so the empty part still reads as a scale.
    val seg = fw / VolumeMeter.SEGMENTS
    for (i in 1 until VolumeMeter.SEGMENTS) {
        drawRect(VmEtchLine, Offset(fx + seg * i - t / 2f, fy), Size(t, fh))
    }
}
