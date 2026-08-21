// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.R
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.update.UpdateState

@Composable
fun UpdateDialog(state: UpdateState, onDismiss: () -> Unit, onOpenFdroid: () -> Unit) {
    when (state) {
        is UpdateState.Available -> Win98Dialog(
            title = "Update available",
            icon = R.drawable.ic_win_info,
            onDismiss = onDismiss,
            buttons = {
                Win98Button("Later", onClick = onDismiss)
                Win98Button("Open F-Droid", onClick = onOpenFdroid)
            },
        ) {
            DialogCopy("A newer version of Yumi Co. Radio is available on F-Droid.")
        }

        UpdateState.UpToDate -> Win98Dialog(
            title = "No update available",
            icon = R.drawable.ic_win_info,
            onDismiss = onDismiss,
            buttons = { Win98Button("OK", onClick = onDismiss) },
        ) {
            DialogCopy("You already have the latest version available on F-Droid.")
        }

        is UpdateState.Error -> Win98Dialog(
            title = "Update check failed",
            icon = R.drawable.ic_win_info,
            onDismiss = onDismiss,
            buttons = { Win98Button("OK", onClick = onDismiss) },
        ) {
            DialogCopy(state.message, color = Win98.Error)
        }

        UpdateState.Idle, UpdateState.Checking -> Unit
    }
}

@Composable
private fun DialogCopy(text: String, color: Color = Win98.Ink) {
    Text(text, fontFamily = W95FA, fontSize = 11.sp, color = color, lineHeight = 15.sp)
}
