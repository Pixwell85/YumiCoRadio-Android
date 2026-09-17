// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import net.yumicoradio.android.ratings.VoteChoice

internal enum class RadioControlAction { LIKE, PLAY, STOP, DISLIKE }

internal enum class RadioControlIcon {
    LIKE_INACTIVE,
    LIKE_ACTIVE,
    DISLIKE_INACTIVE,
    DISLIKE_ACTIVE,
    PLAY,
    STOP,
}

internal data class RadioControl(
    val action: RadioControlAction,
    val active: Boolean = false,
) {
    val icon: RadioControlIcon
        get() = when (action) {
            RadioControlAction.LIKE -> if (active) RadioControlIcon.LIKE_ACTIVE else RadioControlIcon.LIKE_INACTIVE
            RadioControlAction.DISLIKE -> if (active) RadioControlIcon.DISLIKE_ACTIVE else RadioControlIcon.DISLIKE_INACTIVE
            RadioControlAction.PLAY -> RadioControlIcon.PLAY
            RadioControlAction.STOP -> RadioControlIcon.STOP
        }
}

internal fun radioControlLayout(isPlaying: Boolean, vote: VoteChoice): List<RadioControl> = listOf(
    RadioControl(RadioControlAction.LIKE, vote == VoteChoice.LIKE),
    RadioControl(if (isPlaying) RadioControlAction.STOP else RadioControlAction.PLAY),
    RadioControl(RadioControlAction.DISLIKE, vote == VoteChoice.DISLIKE),
)
