package net.yumicoradio.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import net.yumicoradio.android.playback.AudioLevels
import net.yumicoradio.android.playback.VuScale

/** The site's meter colours: theme accent, then amber, then red (`js/yumiPlayer.js:581`). */
private val Normal = Color(0xFFA6CAF0)
private val Warn = Color(0xFFC8C800)
private val Peak = Color(0xFFC80000)
/** `--visualizer-bar-peak-color` in theme19. White was my own invention, and it showed. */
private val Marker = Color(0xFFFF6600)

/** Unlit segments are drawn, not omitted — the site keeps the whole ladder visible at 15% alpha. */
private const val UNLIT_ALPHA = 0.15f

/**
 * The website's default visualiser — `vumeter`, per `js/yumiPlayer.js:38` — as two stacked
 * 32-segment ladders for left and right, with a peak marker that holds and then slides.
 *
 * The level is real: it comes from the decoded PCM through [AudioLevels], not from an animation.
 *
 * [playing] drives it to silence when stopped. [AudioLevels] holds the last buffer's RMS — it does
 * not decay on its own — so once the stream stops, no new buffers arrive and the meter would freeze
 * at whatever it last read. When not playing the target is forced to zero and the release smoothing
 * walks both channels down to rest.
 *
 * Driven by [withFrameNanos] rather than by recomposition: the audio thread produces levels far
 * faster than the screen refreshes, and recomposing per buffer would burn the frame budget to draw
 * the same thing.
 */
@UnstableApi
@Composable
fun VuMeter(volume: Float, playing: Boolean, modifier: Modifier = Modifier) {
    var levelL by remember { mutableStateOf(0f) }
    var levelR by remember { mutableStateOf(0f) }
    var peakL by remember { mutableStateOf(VuScale.Peak()) }
    var peakR by remember { mutableStateOf(VuScale.Peak()) }

    // The frame loop keyed on Unit runs forever, so it must not close over `volume` or `playing`
    // directly — that captures the value at first composition and never sees a change. The meter
    // would then stay scaled by the first volume (raising it does nothing) and never notice a stop.
    // rememberUpdatedState keeps live handles the loop can read each frame.
    val liveVolume = rememberUpdatedState(volume)
    val livePlaying = rememberUpdatedState(playing)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val v = liveVolume.value
                val targetL = if (livePlaying.value) VuScale.levelFromRms(AudioLevels.left, v) else 0f
                val targetR = if (livePlaying.value) VuScale.levelFromRms(AudioLevels.right, v) else 0f
                levelL = VuScale.smooth(levelL, targetL)
                levelR = VuScale.smooth(levelR, targetR)
                peakL = peakL.step(levelL)
                peakR = peakR.step(levelR)
            }
        }
    }

    // The same dark well as the track readout: the visualiser is part of the same instrument, and
    // in a grey Win9x field it looked bolted on from another machine.
    Column(modifier.fillMaxWidth().lcdWell().padding(horizontal = 6.dp, vertical = 7.dp)) {
        Ladder(levelL, peakL, Modifier.fillMaxWidth().height(10.dp))
        Spacer(Modifier.height(3.dp))
        Ladder(levelR, peakR, Modifier.fillMaxWidth().height(10.dp))
    }
}

@Composable
private fun Ladder(level: Float, peak: VuScale.Peak, modifier: Modifier) {
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val segments = VuScale.SEGMENTS
        val segWidth = (size.width - (segments - 1) * gap) / segments
        val lit = VuScale.litSegments(level)
        val marker = VuScale.peakSegment(peak.value)

        for (s in 0 until segments) {
            val x = s * (segWidth + gap)
            val colour = when {
                s == marker -> Marker
                else -> when (VuScale.zoneOf(s)) {
                    VuScale.Zone.NORMAL -> Normal
                    VuScale.Zone.WARN -> Warn
                    VuScale.Zone.PEAK -> Peak
                }
            }
            drawRect(
                color = colour,
                topLeft = Offset(x, 0f),
                size = Size(segWidth, size.height),
                alpha = if (s < lit || s == marker) 1f else UNLIT_ALPHA,
            )
        }
    }
}
