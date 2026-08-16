// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class BackgroundAccessTest {

    @Test
    fun `notification settings use app details before Android 8`() {
        assertEquals(
            NotificationSettingsDestination.APP_DETAILS,
            notificationSettingsDestination(sdkInt = 24),
        )
        assertEquals(
            NotificationSettingsDestination.APP_NOTIFICATIONS,
            notificationSettingsDestination(sdkInt = 26),
        )
    }

    @Test
    fun `missing notification permission blocks both chat notification paths`() {
        val access = notificationAccess(
            appAllowed = false,
            connectionChannelImportance = NotificationImportance.ENABLED,
            messagesChannelImportance = NotificationImportance.ENABLED,
        )

        assertFalse(access.connectionVisible)
        assertFalse(access.messagesVisible)
    }

    @Test
    fun `a missing channel is pending rather than blocked`() {
        val access = notificationAccess(
            appAllowed = true,
            connectionChannelImportance = null,
            messagesChannelImportance = null,
        )

        assertTrue(access.connectionVisible)
        assertTrue(access.messagesVisible)
    }

    @Test
    fun `each blocked channel affects only its own notification path`() {
        val access = notificationAccess(
            appAllowed = true,
            connectionChannelImportance = NotificationImportance.BLOCKED,
            messagesChannelImportance = NotificationImportance.ENABLED,
        )

        assertFalse(access.connectionVisible)
        assertTrue(access.messagesVisible)
    }

    @Test
    fun `notification action explains a hidden foreground notification`() {
        val access = notificationAccess(
            appAllowed = false,
            connectionChannelImportance = null,
            messagesChannelImportance = null,
        )

        assertEquals(
            "Notification access is off. The chat service may run, but its permanent notification is hidden.",
            notificationAccessSummary(access),
        )
    }

    @Test
    fun `notification action distinguishes the connection channel`() {
        val access = notificationAccess(
            appAllowed = true,
            connectionChannelImportance = NotificationImportance.BLOCKED,
            messagesChannelImportance = NotificationImportance.ENABLED,
        )

        assertEquals(
            "The Chat connection notification channel is disabled.",
            notificationAccessSummary(access),
        )
    }

    @Test
    fun `first joined session explains blocked notifications even when battery is exempt`() {
        assertTrue(
            shouldShowBackgroundPrompt(
                hasSession = true,
                stayConnected = true,
                batteryExemption = BatteryExemption.ALLOWED,
                notificationNeedsAttention = true,
                dismissed = false,
            ),
        )
    }

    @Test
    fun `background prompt respects dismissal and live session requirements`() {
        assertFalse(
            shouldShowBackgroundPrompt(true, true, BatteryExemption.RESTRICTED, true, dismissed = true),
        )
        assertFalse(
            shouldShowBackgroundPrompt(false, true, BatteryExemption.RESTRICTED, true, dismissed = false),
        )
    }
}
