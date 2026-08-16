// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    // Bumped whenever we are launched or resumed from a chat notification, so the shell can open the
    // Chat tab. A counter, not a flag: two taps in a row must both register.
    private val openChatSignal = mutableStateOf(0)

    // The mirror for the media notification, so tapping it lands on the Player even when the app was
    // backgrounded on another tab. Without it, a media tap would only avoid the chat, not reach the
    // player.
    private val openPlayerSignal = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(EXTRA_OPEN_CHAT, false) == true) openChatSignal.value++
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) openPlayerSignal.value++
        setContent {
            // The view model is hoisted above the theme: the theme is one of the things it holds.
            val vm: PlayerViewModel = viewModel()
            val dark by vm.darkMode.collectAsState()
            Win98Theme(dark = dark) {
                LaunchedEffect(Unit) { vm.bind() }
                var showSleep by remember { mutableStateOf(false) }

                Shell(
                    vm = vm,
                    openChatSignal = openChatSignal.value,
                    openPlayerSignal = openPlayerSignal.value,
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

    // The activity is singleTop, so a notification tapped while it is already running arrives here
    // rather than through onCreate — without this, tapping a chat notification reopened the app on
    // whatever tab it was last on instead of the chat.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CHAT, false)) openChatSignal.value++
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerSignal.value++
    }

    companion object {
        /** Set on the chat notification's content intent to ask the shell to open the Chat tab. */
        const val EXTRA_OPEN_CHAT = "net.yumicoradio.android.OPEN_CHAT"

        /** Set on the media notification's content intent to ask the shell to open the Player. */
        const val EXTRA_OPEN_PLAYER = "net.yumicoradio.android.OPEN_PLAYER"

        // Distinct request codes per destination. PendingIntent identity ignores extras and compares
        // by request code + Intent.filterEquals (component/action/data) — both these intents target
        // MainActivity with no action or data, so a shared request code would collapse them into one
        // slot and the media notification would inherit the chat intent's OPEN_CHAT extra.
        const val REQ_OPEN_PLAYER = 1
        const val REQ_OPEN_CHAT = 2
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
