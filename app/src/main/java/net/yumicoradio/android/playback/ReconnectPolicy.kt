// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

class ReconnectPolicy(private val baseMs: Long = 1000, private val maxMs: Long = 30_000) {
    fun delayForAttempt(attempt: Int): Long {
        val n = (attempt - 1).coerceAtLeast(0)
        val shifted = baseMs shl n.coerceAtMost(30)   // avoid overflow
        return shifted.coerceIn(baseMs, maxMs)
    }
}
