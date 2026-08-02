// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import java.util.concurrent.atomic.AtomicInteger

/**
 * The lock-free bridge between the equaliser UI and the audio thread.
 *
 * A process singleton for the same reason as [AudioLevels]: the audio sink is built deep inside
 * player construction, with no handle the ViewModel could reach.
 *
 * **Nothing here blocks, on purpose.** The UI writes [enabled] and [gains] (both volatile) and bumps
 * [generation]; the [EqualizerProcessor] reads them and rebuilds its coefficients on the first buffer
 * after the generation changes. An earlier version guarded these with `@Synchronized` and had the
 * processor's `onConfigure` call back in — so a UI-thread write could hold the monitor while the
 * playback thread was mid-`configure()`, stalling sink setup and leaving playback dead until the next
 * write happened to release the lock in the right order. No monitor is ever taken on the audio path.
 */
object Equalizer {

    @Volatile
    var enabled: Boolean = false
        private set

    @Volatile
    var gains: List<Int> = EqualizerSpec.ZERO_GAINS
        private set

    // Bumped on every change so the processor can tell, with one cheap read, whether to rebuild.
    private val gen = AtomicInteger(0)
    val generation: Int get() = gen.get()

    val isEnabled: Boolean get() = enabled
    fun currentGains(): List<Int> = gains

    fun setEnabled(on: Boolean) {
        enabled = on
        gen.incrementAndGet()
    }

    fun setGains(newGains: List<Int>) {
        gains = newGains.map { it.coerceIn(EqualizerSpec.MIN_DB, EqualizerSpec.MAX_DB) }
        gen.incrementAndGet()
    }
}
