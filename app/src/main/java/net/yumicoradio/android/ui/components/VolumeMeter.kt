package net.yumicoradio.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Pure logic for the LED volume meter, split out so it is JVM-testable without Compose.
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

private fun litColor(tier: VolumeMeter.Tier): Color = when (tier) {
    VolumeMeter.Tier.GREEN -> Color(0xFF00FF00)
    VolumeMeter.Tier.ORANGE -> Color(0xFFFF8800)
    VolumeMeter.Tier.RED -> Color(0xFFFF0000)
}

private fun dimColor(tier: VolumeMeter.Tier): Color = when (tier) {
    VolumeMeter.Tier.GREEN -> Color(0xFF003300)
    VolumeMeter.Tier.ORANGE -> Color(0xFF331100)
    VolumeMeter.Tier.RED -> Color(0xFF330000)
}

/** The outset border tones: a lit top-left edge over a dark bottom-right one. */
private val SegmentEdgeLight = Color(0x66FFFFFF)
private val SegmentEdgeDark = Color(0xCC000000)

/**
 * Ten LED segments; drag or tap anywhere across the bar sets [volume] (0.0–1.0) via [onVolume].
 * The bar is 24dp tall (a comfortable touch target) while the segments themselves stay small.
 */
@Composable
fun VolumeMeterBar(volume: Float, onVolume: (Float) -> Unit, modifier: Modifier = Modifier) {
    var widthPx by remember { mutableStateOf(1) }
    val lit = VolumeMeter.litSegments(volume)
    Row(
        modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .pointerInput(Unit) {
                detectTapGestures { pos -> onVolume((pos.x / widthPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onVolume((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until VolumeMeter.SEGMENTS) {
            val tier = VolumeMeter.tier(i)
            val color = if (i < lit) litColor(tier) else dimColor(tier)
            Segment(color)
        }
    }
}

/**
 * One LED. The 1px two-tone border is the site's `.led-segment` outset bezel — kept at one px on
 * purpose, since the 2dp window bevel would leave nothing of a segment this narrow.
 *
 * Unlit segments carry the same bezel, so the empty part of the meter still reads as a row of LEDs
 * rather than a dark gap.
 */
@Composable
private fun RowScope.Segment(color: Color) {
    Spacer(
        Modifier
            .padding(horizontal = 1.dp)
            .weight(1f)
            .height(16.dp)
            .background(color)
            .drawBehind {
                val t = 1.dp.toPx()
                drawRect(SegmentEdgeLight, Offset(0f, 0f), Size(size.width, t))
                drawRect(SegmentEdgeLight, Offset(0f, 0f), Size(t, size.height))
                drawRect(SegmentEdgeDark, Offset(0f, size.height - t), Size(size.width, t))
                drawRect(SegmentEdgeDark, Offset(size.width - t, 0f), Size(t, size.height))
            },
    )
}
