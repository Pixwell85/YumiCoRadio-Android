package net.yumicoradio.android.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.components.tappable
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Theme
import net.yumicoradio.android.ui.theme.Win98Type

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // The view model is hoisted above the theme: the theme is one of the things it holds.
            val vm: PlayerViewModel = viewModel()
            val dark by vm.darkMode.collectAsState()
            Win98Theme(dark = dark) {
                LaunchedEffect(Unit) { vm.bind() }
                var showSleep by remember { mutableStateOf(false) }

                Shell(
                    vm = vm,
                    onShare = { text ->
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, text), null))
                    },
                    onSleep = { showSleep = true },
                    // Minimise means what it says: background the app, keep playing.
                    onMinimize = { moveTaskToBack(true) },
                    // Closing the root window quits — unlike Back and Home, which keep playing.
                    onQuit = { vm.quit(); finishAndRemoveTask() },
                )

                if (showSleep) SleepDialog(onPick = { min ->
                    showSleep = false
                    vm.startSleep(min)
                }, onDismiss = { showSleep = false })
            }
        }
    }
}

@Composable
private fun SleepDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    Win98Dialog(
        title = "Sleep timer",
        onDismiss = onDismiss,
        buttons = { Win98Button("Cancel", onClick = onDismiss) },
    ) {
        listOf(15, 30, 45, 60).forEach { m ->
            SleepChoice("$m minutes") { onPick(m) }
        }
        SleepChoice("Off") { onPick(0) }
    }
}

/** A row in the sleep list: a tap target, not a button — the idiom's list-item, like the site's. */
@Composable
private fun SleepChoice(label: String, onClick: () -> Unit) {
    Text(
        label,
        fontFamily = W95FA,
        fontSize = Win98Type.Body,
        color = Win98.Ink,
        modifier = Modifier.fillMaxWidth().tappable(onClick).padding(vertical = 8.dp, horizontal = 2.dp),
    )
}
