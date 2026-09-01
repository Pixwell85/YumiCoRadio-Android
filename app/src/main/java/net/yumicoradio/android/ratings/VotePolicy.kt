// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

fun nextVoteChoice(current: VoteChoice, tapped: VoteChoice): VoteChoice =
    if (current == tapped) VoteChoice.NONE else tapped
