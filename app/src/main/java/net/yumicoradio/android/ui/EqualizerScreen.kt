// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.playback.EqualizerSpec
import net.yumicoradio.android.ui.components.*
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import kotlin.math.roundToInt

/**
 * The graphic equaliser window: an on/off, a preset chooser, and ten vertical band sliders — the
 * site's Graphic Equalizer, in the app's Win9x idiom. Stateless over the settings: every change is
 * reported up to the ViewModel, which drives the live filter and persistence.
 */
@Composable
fun EqualizerDialog(
    enabled: Boolean,
    gains: List<Int>,
    preset: String?,
    onToggle: (Boolean) -> Unit,
    onBand: (Int, Int) -> Unit,
    onPreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showPresets by remember { mutableStateOf(false) }

    Win98Dialog(
        title = "Graphic Equalizer",
        onDismiss = onDismiss,
        buttons = { Win98Button("Close") { onDismiss() } },
    ) {
        // Its own row: Win98Checkbox lays itself out full-width, so anything beside it in a Row would
        // be crushed to nothing (that was the preset label wrapping one letter per line).
        Win98Checkbox(checked = enabled, label = "Equalizer", onToggle = onToggle)

        Spacer(Modifier.height(8.dp))

        // No fillMaxWidth / weight here: the dialog measures its width from the fader row, and a
        // stretched preset row would run past it. Let this row wrap its own content, left-aligned.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Preset:", fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.background(Win98.Face).pressable { showPresets = true }.raised()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(preset ?: "Custom", fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EqualizerSpec.BANDS.forEachIndexed { i, (_, label) ->
                val g = gains.getOrElse(i) { 0 }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${if (g > 0) "+" else ""}$g",
                        fontFamily = W95FA, fontSize = 9.sp, color = Win98.Ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    EqSlider(gain = g, enabled = enabled, onGain = { onBand(i, it) })
                    Spacer(Modifier.height(3.dp))
                    Text(label, fontFamily = W95FA, fontSize = 9.sp, color = Win98.InkDim)
                }
            }
        }
    }

    if (showPresets) {
        PresetPicker(
            current = preset,
            onPick = { onPreset(it); showPresets = false },
            onDismiss = { showPresets = false },
        )
    }
}

/** One vertical band fader: a sunken track with a raised thumb, dragged or tapped to set the gain. */
@Composable
private fun EqSlider(gain: Int, enabled: Boolean, onGain: (Int) -> Unit) {
    val min = EqualizerSpec.MIN_DB
    val span = (EqualizerSpec.MAX_DB - min).toFloat()
    val thumbH = with(LocalDensity.current) { 8.dp.toPx() }

    BoxWithConstraints(Modifier.width(26.dp).height(110.dp)) {
        val h = constraints.maxHeight.toFloat()
        val travel = (h - thumbH).coerceAtLeast(1f)

        val yToGain = { y: Float ->
            val frac = (1f - (y - thumbH / 2f) / travel).coerceIn(0f, 1f) // 0 bottom, 1 top
            (min + frac * span).roundToInt().coerceIn(min, EqualizerSpec.MAX_DB)
        }
        val handle = { y: Float -> yToGain(y).let { if (it != gain) onGain(it) } }

        // The thumb's top, from the gain: +12 rides at the top, -12 at the bottom.
        val frac = (gain - min) / span
        val thumbTop = (travel * (1f - frac)).roundToInt()

        // Track down the middle.
        Box(Modifier.align(Alignment.Center).width(6.dp).fillMaxHeight().background(Win98.Sunken).sunken())
        // A hairline at 0 dB, so the centre is findable at a glance.
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(1.dp)
                .background(Win98.Shadow),
        )
        // The thumb.
        Box(
            Modifier.fillMaxWidth().height(8.dp).offset { IntOffset(0, thumbTop) }
                .background(Win98.Face).raised(),
        )
        // Gesture overlay on top so the whole column is the touch target.
        Box(
            Modifier.matchParentSize()
                .pointerInput(enabled) { if (enabled) detectTapGestures { handle(it.y) } }
                .pointerInput(enabled) {
                    if (enabled) detectVerticalDragGestures { change, _ -> handle(change.position.y) }
                },
        )
    }
}

/** The 22 presets, in the site's order; the current one shown in bold. */
@Composable
private fun PresetPicker(current: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Win98Dialog(
        title = "Presets",
        onDismiss = onDismiss,
        buttons = { Win98Button("Cancel") { onDismiss() } },
    ) {
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            EqualizerSpec.PRESETS.keys.forEach { name ->
                Text(
                    name,
                    fontFamily = W95FA, fontSize = 12.sp, color = Win98.Ink,
                    fontWeight = if (name == current) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.fillMaxWidth().tappable { onPick(name) }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                )
            }
        }
    }
}
