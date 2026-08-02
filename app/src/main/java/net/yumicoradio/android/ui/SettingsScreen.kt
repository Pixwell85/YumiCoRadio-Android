// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.playback.StreamQuality
import net.yumicoradio.android.ui.components.Win98Checkbox
import net.yumicoradio.android.ui.components.Win98Fieldset
import net.yumicoradio.android.ui.components.Win98Radio
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

/**
 * The app-wide settings: appearance and audio. Everything specific to the chat lives in the chat's
 * own Options dialog (the toolbar button), the way the website groups it — so chat settings are all
 * in one place, next to the chat.
 */
@Composable
fun ColumnScope.SettingsContent(vm: PlayerViewModel) {
    val quality by vm.quality.collectAsState()
    val darkMode by vm.darkMode.collectAsState()

    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Win98Fieldset("Appearance") {
            // No description: "Dark mode" explains itself, and naming the website's theme only
            // means something to someone who already knows the website.
            Win98Checkbox(
                checked = darkMode,
                label = "Dark mode",
                onToggle = { vm.setDarkMode(it) },
            )
        }

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("Playback") {
            Text("Stream quality", fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
            Spacer(Modifier.height(2.dp))
            StreamQuality.entries.forEach { q ->
                Win98Radio(
                    selected = q == quality,
                    label = "${q.kbps} kbps ${if (q == StreamQuality.AAC64) "(AAC)" else "(MP3)"}",
                    onSelect = { vm.setQuality(q) },
                )
            }
        }
    }
}
