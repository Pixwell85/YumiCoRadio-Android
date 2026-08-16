// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ForegroundServiceStart { LEGACY_SERVICE, FOREGROUND_SERVICE }

fun foregroundServiceStartFor(sdkInt: Int): ForegroundServiceStart =
    if (sdkInt >= 26) {
        ForegroundServiceStart.FOREGROUND_SERVICE
    } else {
        ForegroundServiceStart.LEGACY_SERVICE
    }

enum class ForegroundPromotion { LEGACY, REMOTE_MESSAGING }

fun foregroundPromotionFor(sdkInt: Int): ForegroundPromotion =
    if (sdkInt >= 34) ForegroundPromotion.REMOTE_MESSAGING else ForegroundPromotion.LEGACY

fun shouldHoldCpuWakeLock(
    maximumReliability: Boolean,
    stayConnected: Boolean,
    hasSession: Boolean,
): Boolean = maximumReliability && stayConnected && hasSession

data class BackgroundProtectionStatus(
    val serviceRunning: Boolean = false,
    val wifiLockHeld: Boolean = false,
    val cpuLockHeld: Boolean = false,
    val lastError: String? = null,
)

object BackgroundProtectionMonitor {
    private val mutable = MutableStateFlow(BackgroundProtectionStatus())
    val status: StateFlow<BackgroundProtectionStatus> = mutable.asStateFlow()

    fun update(block: (BackgroundProtectionStatus) -> BackgroundProtectionStatus) {
        mutable.update(block)
    }
}
