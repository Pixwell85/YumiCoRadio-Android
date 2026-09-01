// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri

enum class NotificationImportance { ENABLED, BLOCKED }

enum class NotificationSettingsDestination { APP_DETAILS, APP_NOTIFICATIONS }

fun notificationSettingsDestination(sdkInt: Int): NotificationSettingsDestination =
    if (sdkInt >= 26) {
        NotificationSettingsDestination.APP_NOTIFICATIONS
    } else {
        NotificationSettingsDestination.APP_DETAILS
    }

data class NotificationAccess(
    val appAllowed: Boolean,
    val connectionChannelAllowed: Boolean,
    val messagesChannelAllowed: Boolean,
) {
    val connectionVisible: Boolean get() = appAllowed && connectionChannelAllowed
    val messagesVisible: Boolean get() = appAllowed && messagesChannelAllowed
    val needsAttention: Boolean get() = !connectionVisible || !messagesVisible
}

fun notificationAccess(
    appAllowed: Boolean,
    connectionChannelImportance: NotificationImportance?,
    messagesChannelImportance: NotificationImportance?,
): NotificationAccess = NotificationAccess(
    appAllowed = appAllowed,
    connectionChannelAllowed = connectionChannelImportance != NotificationImportance.BLOCKED,
    messagesChannelAllowed = messagesChannelImportance != NotificationImportance.BLOCKED,
)

fun readNotificationAccess(context: Context): NotificationAccess {
    val compat = NotificationManagerCompat.from(context)
    val manager = context.getSystemService(NotificationManager::class.java)

    fun channel(id: String): NotificationImportance? {
        if (Build.VERSION.SDK_INT < 26) return NotificationImportance.ENABLED
        val current = manager.getNotificationChannel(id) ?: return null
        return if (current.importance == NotificationManager.IMPORTANCE_NONE) {
            NotificationImportance.BLOCKED
        } else {
            NotificationImportance.ENABLED
        }
    }

    return notificationAccess(
        appAllowed = compat.areNotificationsEnabled(),
        connectionChannelImportance = channel(ChatConnectionService.CONNECTION_CHANNEL),
        messagesChannelImportance = channel(ChatConnectionService.MESSAGES_CHANNEL),
    )
}

fun notificationSettingsIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= 26) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        )
    }

fun notificationAccessSummary(access: NotificationAccess): String = when {
    !access.appAllowed ->
        "Notification access is off. The chat service may run, but its permanent notification is hidden."
    !access.connectionChannelAllowed ->
        "The Chat connection notification channel is disabled."
    !access.messagesChannelAllowed ->
        "The Chat messages notification channel is disabled."
    else -> "Chat notifications are allowed."
}

fun backgroundReliabilitySummary(
    stayConnected: Boolean,
    batteryExemption: BatteryExemption,
    notificationAccess: NotificationAccess,
): String = when {
    stayConnected && notificationAccess.needsAttention -> notificationAccessSummary(notificationAccess)
    stayConnected && batteryExemption != BatteryExemption.ALLOWED ->
        batteryExemptionSummary(batteryExemption)
    stayConnected -> "Open to verify battery settings and background protection."
    else -> "Turn on “Stay connected” above to keep the chat live in the background."
}

fun shouldShowBackgroundPrompt(
    hasSession: Boolean,
    stayConnected: Boolean,
    batteryExemption: BatteryExemption,
    notificationNeedsAttention: Boolean,
    dismissed: Boolean,
): Boolean = hasSession && stayConnected && !dismissed &&
    (batteryExemption != BatteryExemption.ALLOWED || notificationNeedsAttention)
