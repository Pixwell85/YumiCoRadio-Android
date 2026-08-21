// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.update

object UpdatePolicy {
    const val INTERVAL_MS = 24L * 60L * 60L * 1000L

    fun shouldCheckAutomatically(enabled: Boolean, now: Long, lastAttempt: Long): Boolean =
        enabled && (lastAttempt <= 0L || now - lastAttempt >= INTERVAL_MS)

    fun shouldShowAutomatically(availableCode: Int, dismissedCode: Int): Boolean =
        availableCode > dismissedCode
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val versionCode: Int) : UpdateState
    data class Error(val message: String) : UpdateState
}
