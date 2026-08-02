// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.chat.Oem
import net.yumicoradio.android.chat.oemGuidance
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Type

/**
 * Explains why the background chat connection needs a battery-optimization exemption, and takes the
 * user to the screens that grant it: the system one for everyone, plus the OEM autostart/battery
 * screen on manufacturers that kill background apps aggressively.
 *
 * [isExempt] only softens the copy — the dialog is still useful when already exempt, since OEM
 * autostart is a separate switch the system exemption does not cover.
 */
@Composable
fun BackgroundReliabilityDialog(
    isExempt: Boolean,
    oem: Oem,
    onOpenBattery: () -> Unit,
    onOpenOem: () -> Unit,
    onDismiss: () -> Unit,
) {
    val guidance = oemGuidance(oem)
    Win98Dialog(
        title = "Keep the chat connected",
        onDismiss = onDismiss,
        buttons = { Win98Button("Close") { onDismiss() } },
    ) {
        Body(
            if (isExempt) {
                "Background activity is already allowed. If the chat still drops when the app is " +
                    "in the background, the steps below help on phones that limit apps harder."
            } else {
                "To keep the live chat connected while the app is in the background, Android needs " +
                    "to be told not to put this app to sleep. Without it, the connection is dropped " +
                    "after a while."
            },
        )
        Spacer(Modifier.height(10.dp))
        Win98Button("Allow background activity") { onOpenBattery() }

        if (guidance != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                guidance.label,
                fontFamily = W95FA, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Win98.Ink,
            )
            Spacer(Modifier.height(4.dp))
            Body(guidance.instruction)
            Spacer(Modifier.height(8.dp))
            Win98Button("Open ${guidance.label}") { onOpenOem() }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        fontFamily = W95FA,
        fontSize = Win98Type.Body,
        lineHeight = Win98Type.BodyLineHeight,
        color = Win98.Ink,
    )
}
