// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import net.yumicoradio.android.ratings.VoteChoice

internal enum class RadioControlAction { LIKE, PLAY, STOP, DISLIKE }

internal data class RadioControl(
    val action: RadioControlAction,
    val active: Boolean = false,
)

internal fun radioControlLayout(isPlaying: Boolean, vote: VoteChoice): List<RadioControl> = listOf(
    RadioControl(RadioControlAction.LIKE, vote == VoteChoice.LIKE),
    RadioControl(if (isPlaying) RadioControlAction.STOP else RadioControlAction.PLAY),
    RadioControl(RadioControlAction.DISLIKE, vote == VoteChoice.DISLIKE),
)
