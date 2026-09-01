// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

enum class VoteChoice(val wire: String) { LIKE("like"), DISLIKE("dislike"), NONE("none");
    companion object { fun from(value: String) = entries.firstOrNull { it.wire == value } }
}
enum class RankingTab(val wire: String) { LIKE("like"), DISLIKE("dislike") }
enum class RankingPeriodType(val wire: String) { DAY("day"), WEEK("week"), MONTH("month") }
enum class MyVotesFilter(val wire: String) { ALL("all"), LIKED("liked"), DISLIKED("disliked") }

data class RatingTrack(val trackId: String, val artist: String, val title: String, val artworkUrl: String?)
data class Ballot(val weekKey: String, val startMs: Long, val endMs: Long)
data class CurrentVote(
    val track: RatingTrack,
    val occurrenceId: String,
    val choice: VoteChoice,
    val ballot: Ballot,
    val nextResetMs: Long,
    val changed: Boolean,
)
data class RankingPeriod(
    val type: RankingPeriodType,
    val key: String,
    val startMs: Long,
    val endMs: Long,
    val current: Boolean,
    val canGoNext: Boolean,
)
data class RankingRow(
    val rank: Int,
    val track: RatingTrack,
    val count: Int,
    val visitorHasMatchingVote: Boolean,
)
data class RankingPage(
    val tab: RankingTab,
    val period: RankingPeriod,
    val nextResetMs: Long,
    val page: Int,
    val totalRows: Int,
    val totalPages: Int,
    val rows: List<RankingRow>,
)
data class MyVoteRow(
    val track: RatingTrack,
    val latestChoice: VoteChoice,
    val currentChoice: VoteChoice,
    val currentWeekKey: String?,
    val currentWeekEndMs: Long?,
    val likedWeeks: Int,
    val dislikedWeeks: Int,
    val firstVoteMs: Long,
    val latestVoteMs: Long,
)
data class MyVotesPage(
    val filter: MyVotesFilter,
    val page: Int,
    val totalRows: Int,
    val totalPages: Int,
    val rows: List<MyVoteRow>,
)
data class RatingsSnapshot(
    val currentVote: CurrentVote? = null,
    val loading: Boolean = false,
    val message: String? = null,
)

class RatingsApiException(val status: Int, val code: String, override val message: String) : Exception(message)
