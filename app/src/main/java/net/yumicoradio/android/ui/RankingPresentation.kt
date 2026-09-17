// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.annotation.DrawableRes
import java.util.Calendar
import java.util.TimeZone
import net.yumicoradio.android.R
import net.yumicoradio.android.ratings.RankingPeriodType
import net.yumicoradio.android.ratings.RankingTab
import net.yumicoradio.android.ratings.VoteChoice

internal fun rankingResetLabel(
    type: RankingPeriodType,
    nowMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    val remainingSeconds = ((nextRankingBoundaryMs(type, nowMs, timeZone) - nowMs) / 1_000)
        .coerceAtLeast(0)
    val days = remainingSeconds / 86_400
    val hours = remainingSeconds % 86_400 / 3_600
    val minutes = remainingSeconds % 3_600 / 60
    val seconds = remainingSeconds % 60
    val duration = buildString {
        if (days > 0) append(days).append("d ")
        append(hours.toString().padStart(2, '0')).append(':')
        append(minutes.toString().padStart(2, '0')).append(':')
        append(seconds.toString().padStart(2, '0'))
    }
    val prefix = when (type) {
        RankingPeriodType.DAY -> "Daily reset in"
        RankingPeriodType.WEEK -> "Weekly reset in"
        RankingPeriodType.MONTH -> "Monthly reset in"
    }
    return "$prefix $duration"
}

private fun nextRankingBoundaryMs(type: RankingPeriodType, nowMs: Long, timeZone: TimeZone): Long {
    val boundary = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    when (type) {
        RankingPeriodType.DAY -> boundary.add(Calendar.DAY_OF_MONTH, 1)
        RankingPeriodType.WEEK -> {
            val daysUntilMonday = (Calendar.MONDAY - boundary.get(Calendar.DAY_OF_WEEK) + 7) % 7
            boundary.add(Calendar.DAY_OF_MONTH, if (daysUntilMonday == 0) 7 else daysUntilMonday)
        }
        RankingPeriodType.MONTH -> {
            boundary.set(Calendar.DAY_OF_MONTH, 1)
            boundary.add(Calendar.MONTH, 1)
        }
    }
    return boundary.timeInMillis
}

@DrawableRes
internal fun rankingVoteIcon(tab: RankingTab): Int = when (tab) {
    RankingTab.LIKE -> R.drawable.ic_vote_heart
    RankingTab.DISLIKE -> R.drawable.ic_vote_heart_broken
}

@DrawableRes
internal fun rankingVoteIcon(choice: VoteChoice): Int = when (choice) {
    VoteChoice.DISLIKE -> R.drawable.ic_vote_heart_broken
    VoteChoice.LIKE, VoteChoice.NONE -> R.drawable.ic_vote_heart
}
