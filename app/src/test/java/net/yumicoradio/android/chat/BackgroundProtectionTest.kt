// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class BackgroundProtectionTest {

    @Test
    fun `Android 7 starts the chat service through the legacy API`() {
        assertEquals(
            ForegroundServiceStart.LEGACY_SERVICE,
            foregroundServiceStartFor(sdkInt = 24),
        )
        assertEquals(
            ForegroundServiceStart.FOREGROUND_SERVICE,
            foregroundServiceStartFor(sdkInt = 26),
        )
    }

    @Test
    fun `Android 14 promotes chat with its declared foreground service type`() {
        assertEquals(
            ForegroundPromotion.LEGACY,
            foregroundPromotionFor(sdkInt = 33),
        )
        assertEquals(
            ForegroundPromotion.REMOTE_MESSAGING,
            foregroundPromotionFor(sdkInt = 34),
        )
    }

    @Test
    fun `CPU lock requires every reliability condition`() {
        assertTrue(shouldHoldCpuWakeLock(maximumReliability = true, stayConnected = true, hasSession = true))
        assertFalse(shouldHoldCpuWakeLock(maximumReliability = false, stayConnected = true, hasSession = true))
        assertFalse(shouldHoldCpuWakeLock(maximumReliability = true, stayConnected = false, hasSession = true))
        assertFalse(shouldHoldCpuWakeLock(maximumReliability = true, stayConnected = true, hasSession = false))
    }

    @Test
    fun `service status records locks without losing prior state`() {
        val initial = BackgroundProtectionStatus()
        val running = initial.copy(serviceRunning = true, wifiLockHeld = true)
        val maximum = running.copy(cpuLockHeld = true)

        assertTrue(maximum.serviceRunning)
        assertTrue(maximum.wifiLockHeld)
        assertTrue(maximum.cpuLockHeld)
        assertNull(maximum.lastError)
    }
}
