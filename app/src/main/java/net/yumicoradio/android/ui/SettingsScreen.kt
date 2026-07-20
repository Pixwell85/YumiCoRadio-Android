package net.yumicoradio.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.chat.NotificationMode
import net.yumicoradio.android.playback.StreamQuality
import net.yumicoradio.android.ui.components.Win98Checkbox
import net.yumicoradio.android.ui.components.Win98Fieldset
import net.yumicoradio.android.ui.components.Win98Radio
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

/**
 * Every setting in one place, grouped the way the website groups them.
 *
 * The chat's options used to hide behind a gear inside the chat window; settings split across two
 * places are settings people cannot find.
 */
@Composable
fun ColumnScope.SettingsContent(vm: PlayerViewModel, chat: ChatViewModel) {
    val quality by vm.quality.collectAsState()
    val notifyMode by chat.notificationMode.collectAsState()
    val stayConnected by chat.stayConnected.collectAsState()
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

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("Live Chat") {
            Text("Notify me about", fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
            Spacer(Modifier.height(2.dp))
            NotificationMode.entries.forEach { mode ->
                Win98Radio(
                    selected = mode == notifyMode,
                    label = mode.label,
                    onSelect = { chat.setNotificationMode(mode) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Win98Checkbox(
                checked = stayConnected,
                label = "Stay connected in the background",
                // Android requires the permanent notification; better said here than discovered.
                description = "Keeps a permanent notification and uses battery.",
                onToggle = { chat.setStayConnected(it) },
            )
        }
    }
}
