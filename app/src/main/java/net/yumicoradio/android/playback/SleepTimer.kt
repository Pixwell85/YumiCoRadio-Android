// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

/** Pure countdown. Production drives tick() from a coroutine; tests drive it manually. */
class SleepTimer(private val nowMs: () -> Long) {
    private var deadline: Long? = null
    private var onExpire: (() -> Unit)? = null

    val isActive: Boolean get() = deadline != null

    fun start(durationMs: Long, onExpire: () -> Unit) {
        deadline = nowMs() + durationMs
        this.onExpire = onExpire
    }
    fun remainingMs(): Long {
        val d = deadline ?: return 0
        return (d - nowMs()).coerceAtLeast(0)
    }
    fun tick() {
        val d = deadline ?: return
        if (nowMs() >= d) {
            val cb = onExpire
            cancel()
            cb?.invoke()
        }
    }
    fun cancel() { deadline = null; onExpire = null }
}
