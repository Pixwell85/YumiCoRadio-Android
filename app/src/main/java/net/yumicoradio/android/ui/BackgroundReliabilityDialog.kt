// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.chat.Oem
import net.yumicoradio.android.chat.NotificationAccess
import net.yumicoradio.android.chat.BackgroundProtectionStatus
import net.yumicoradio.android.chat.BatteryExemption
import net.yumicoradio.android.chat.batteryExemptionSummary
import net.yumicoradio.android.chat.notificationAccessSummary
import net.yumicoradio.android.chat.oemGuidance
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Checkbox
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Type

/**
 * Explains why the background chat connection needs a battery-optimization exemption, and takes the
 * user to the screens that grant it: the system one for everyone, plus the OEM autostart/battery
 * screen on manufacturers that kill background apps aggressively.
 *
 * Android's standard exemption is only one status here: OEM autostart remains a separate manual
 * switch that the platform does not expose to applications.
 */
@Composable
fun BackgroundReliabilityDialog(
    batteryExemption: BatteryExemption,
    notificationAccess: NotificationAccess,
    protectionStatus: BackgroundProtectionStatus,
    maximumReliability: Boolean,
    oem: Oem,
    onRequestNotifications: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenOem: () -> Unit,
    onToggleMaximumReliability: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val guidance = oemGuidance(oem)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Win98Dialog(
        title = "Keep the chat connected",
        onDismiss = onDismiss,
        buttons = { Win98Button("Close") { onDismiss() } },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            Section("Android battery")
            Body(batteryExemptionSummary(batteryExemption))
            Spacer(Modifier.height(8.dp))
            Win98Button("Allow background activity") { onOpenBattery() }

            Spacer(Modifier.height(12.dp))
            Section("Notifications")
            Body(notificationAccessSummary(notificationAccess))
            if (!notificationAccess.appAllowed) {
                Spacer(Modifier.height(8.dp))
                Win98Button("Allow notifications") { onRequestNotifications() }
            }
            if (notificationAccess.needsAttention) {
                Spacer(Modifier.height(8.dp))
                Win98Button("Open notification settings") { onOpenNotificationSettings() }
            }

            Spacer(Modifier.height(12.dp))
            Section("Protection status")
            StatusLine("Foreground service", protectionStatus.serviceRunning)
            StatusLine("Wi-Fi lock", protectionStatus.wifiLockHeld)
            Body(
                if (maximumReliability) {
                    if (protectionStatus.cpuLockHeld) "CPU lock: active" else "CPU lock: waiting for an active session"
                } else {
                    "CPU lock: disabled"
                },
            )
            protectionStatus.lastError?.let {
                Spacer(Modifier.height(4.dp))
                Body("Last protection error: $it")
            }
            Spacer(Modifier.height(8.dp))
            Win98Checkbox(
                checked = maximumReliability,
                label = "Maximum reliability",
                description = "Keeps the CPU awake when Android permits it. Doze or HyperOS may " +
                    "still pause the network. Uses more battery.",
                onToggle = onToggleMaximumReliability,
            )

            if (guidance != null) {
                Spacer(Modifier.height(12.dp))
                Section(guidance.label)
                Body(guidance.instruction)
                Spacer(Modifier.height(8.dp))
                Win98Button("Open ${guidance.label}") { onOpenOem() }
            }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        fontFamily = W95FA, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Win98.Ink,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun StatusLine(label: String, active: Boolean) {
    Body("$label: ${if (active) "active" else "not active"}")
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
