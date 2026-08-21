// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import net.yumicoradio.android.chat.ChatFontSize
import net.yumicoradio.android.chat.NickColors
import net.yumicoradio.android.chat.NotificationMode
import net.yumicoradio.android.chat.NotificationAccess
import net.yumicoradio.android.chat.BatteryExemption
import net.yumicoradio.android.chat.batteryExemptionSummary
import net.yumicoradio.android.chat.notificationAccessSummary
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Checkbox
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.components.Win98Radio
import net.yumicoradio.android.ui.components.pressable
import net.yumicoradio.android.ui.components.sunken
import net.yumicoradio.android.ui.components.tappable
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

/**
 * The chat's "Options" dialog. For now it holds one thing — the nickname-colour picker — but it is
 * named for the window it mirrors on the site, so more chat options can move in later.
 *
 * [selected] is the current override (`""` = Auto). [nick] is the name shown in the live preview.
 * [onPick] fires on every swatch tap so the preview and the chat update at once; [onDismiss] closes.
 */
@Composable
fun ChatOptionsDialog(
    selected: String,
    nick: String,
    rememberPassword: Boolean,
    onToggleRemember: (Boolean) -> Unit,
    showReserved: Boolean,
    notifyMode: NotificationMode,
    onNotify: (NotificationMode) -> Unit,
    fontSize: ChatFontSize,
    onFontSize: (ChatFontSize) -> Unit,
    showTimestamps: Boolean,
    onToggleTimestamps: (Boolean) -> Unit,
    separatePresenceActivity: Boolean,
    onToggleSeparatePresence: (Boolean) -> Unit,
    stayConnected: Boolean,
    onToggleStay: (Boolean) -> Unit,
    batteryExemption: BatteryExemption,
    notificationAccess: NotificationAccess,
    onOpenBackgroundReliability: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Every chat option lives here now, so the list outgrows one screen — cap it and scroll, with OK
    // pinned below.
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Win98Dialog(
        title = "Chat Options",
        onDismiss = onDismiss,
        buttons = { Win98Button("OK") { onDismiss() } },
    ) {
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            Text("Nickname colour", fontFamily = W95FA, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Win98.Ink)
            Spacer(Modifier.height(6.dp))

            // Live preview: the name the way the chat will draw it.
            val previewName = nick.ifBlank { "You" }
            val previewColor = Color(
                (if (selected.isBlank()) NickColors.forNick(previewName) else selected).toColorInt(),
            )
            Row(
                Modifier.fillMaxWidth().sunken().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Preview:", fontFamily = W95FA, fontSize = 11.sp, color = Win98.InkDim)
                Spacer(Modifier.width(8.dp))
                Text("<$previewName>", fontFamily = W95FA, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = previewColor)
            }
            Spacer(Modifier.height(8.dp))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Auto first, as on the site: no override, hash-derived colour.
                AutoChip(isSelected = selected.isBlank(), onClick = { onPick("") })
                NickColors.NAMED.forEach { (hex, name) ->
                    Swatch(
                        color = Color(hex.toColorInt()),
                        name = name,
                        isSelected = selected.equals(hex, ignoreCase = true),
                        onClick = { onPick(hex) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Notify me about", fontFamily = W95FA, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Win98.Ink)
            Spacer(Modifier.height(2.dp))
            NotificationMode.entries.forEach { mode ->
                Win98Radio(
                    selected = mode == notifyMode,
                    label = mode.label,
                    onSelect = { onNotify(mode) },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Text size", fontFamily = W95FA, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Win98.Ink)
            Spacer(Modifier.height(2.dp))
            ChatFontSize.entries.forEach { size ->
                Win98Radio(
                    selected = size == fontSize,
                    label = size.label,
                    onSelect = { onFontSize(size) },
                )
            }

            Spacer(Modifier.height(8.dp))
            Win98Checkbox(
                checked = showTimestamps,
                label = "Show timestamps",
                description = "A [HH:mm] time before each message, in channels and PMs.",
                onToggle = onToggleTimestamps,
            )

            Spacer(Modifier.height(8.dp))
            Win98Checkbox(
                checked = separatePresenceActivity,
                label = "Move join and leave notices to Activity",
                description = "When off, these notices appear in the regular channels.",
                onToggle = onToggleSeparatePresence,
            )

            Spacer(Modifier.height(8.dp))
            Win98Checkbox(
                checked = stayConnected,
                label = "Stay connected in the background",
                description = "Keeps a permanent notification and uses battery.",
                onToggle = onToggleStay,
            )
            Spacer(Modifier.height(6.dp))
            // Only a live problem when the user actually wants a background connection; with "Stay
            // connected" off, being non-exempt is moot, so it is not dressed as a warning.
            val atRisk = stayConnected &&
                (batteryExemption != BatteryExemption.ALLOWED || notificationAccess.needsAttention)
            Column(
                Modifier.fillMaxWidth().tappable { onOpenBackgroundReliability() }.padding(vertical = 4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (atRisk) "⚠ Background reliability" else "Background reliability",
                        fontFamily = W95FA, fontSize = 11.sp,
                        color = if (atRisk) Win98.Error else Win98.Ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("Set up ›", fontFamily = W95FA, fontSize = 11.sp, color = Win98.InkDim)
                }
                Text(
                    when {
                        stayConnected && notificationAccess.needsAttention ->
                            notificationAccessSummary(notificationAccess)
                        atRisk -> batteryExemptionSummary(batteryExemption)
                        stayConnected -> "Open to verify HyperOS settings and protection status."
                        else -> "Turn on “Stay connected” above to keep the chat live in the background."
                    },
                    fontFamily = W95FA, fontSize = 10.sp, color = Win98.InkDim,
                )
            }

            // Only a reserved nickname has a password to remember; hide the section for everyone else.
            if (showReserved) {
                Spacer(Modifier.height(10.dp))
                Text("Reserved nickname", fontFamily = W95FA, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Win98.Ink)
                Win98Checkbox(
                    checked = rememberPassword,
                    label = "Remember password",
                    onToggle = onToggleRemember,
                    description = "Stored encrypted on this device. Anyone who can unlock your phone could then connect as you.",
                )
            }
        }
    }
}

/** A colour cell; the selected one wears a heavy ink border so the pick is unmistakable. */
@Composable
private fun Swatch(color: Color, name: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .semantics { contentDescription = name }
            .then(if (isSelected) Modifier.border(2.dp, Win98.Ink) else Modifier.border(1.dp, Win98.Shadow))
            .padding(if (isSelected) 2.dp else 3.dp)
            .background(color)
            .pressable(onClick),
    )
}

/** The "Auto" option, a labelled chip rather than a colour. */
@Composable
private fun AutoChip(isSelected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(30.dp)
            .then(if (isSelected) Modifier.border(2.dp, Win98.Ink) else Modifier.border(1.dp, Win98.Shadow))
            .background(Win98.Face)
            .pressable(onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Auto", fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
    }
}
