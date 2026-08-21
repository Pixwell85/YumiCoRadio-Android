// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/** Pure volume state shared by the inline and fullscreen video controls. */
class ChatVideoVolumeState(initialVolume: Float = 1f) {
    var volume: Float = initialVolume.coerceIn(0f, 1f)
        private set

    private var lastAudibleVolume = volume.takeIf { it > 0f } ?: 1f

    fun set(value: Float): Float {
        volume = value.coerceIn(0f, 1f)
        if (volume > 0f) lastAudibleVolume = volume
        return volume
    }

    fun toggleMute(): Float {
        volume = if (volume > 0f) {
            lastAudibleVolume = volume
            0f
        } else {
            lastAudibleVolume
        }
        return volume
    }
}
