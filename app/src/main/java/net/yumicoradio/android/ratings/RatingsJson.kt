// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private fun JsonObject.string(name: String, max: Int = 256): String =
    this[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() && it.length <= max } ?: invalid()
private fun JsonObject.long(name: String): Long =
    this[name]?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 } ?: invalid()
private fun JsonObject.int(name: String, max: Int = 1_000_000): Int =
    this[name]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..max } ?: invalid()
private fun JsonObject.bool(name: String): Boolean = this[name]?.jsonPrimitive?.booleanOrNull ?: invalid()

private fun track(o: JsonObject): RatingTrack {
    val artwork = o["artworkUrl"]?.let { if (it.toString() == "null") null else it.jsonPrimitive.content }
    if (artwork != null && (!artwork.startsWith("https://") || artwork.length > 2_048)) invalid()
    return RatingTrack(o.string("trackId"), o.string("artist"), o.string("title"), artwork?.ifBlank { null })
}

internal fun parseCurrentVote(value: JsonElement): CurrentVote {
    val o = value.jsonObject
    val choice = VoteChoice.from(o.string("choice", 8)) ?: invalid()
    val ballot = o["ballot"]?.jsonObject ?: invalid()
    val parsedBallot = Ballot(ballot.string("weekKey", 10), ballot.long("startMs"), ballot.long("endMs"))
    if (parsedBallot.startMs >= parsedBallot.endMs) invalid()
    return CurrentVote(
        track(o["track"]?.jsonObject ?: invalid()), o.string("occurrenceId"), choice, parsedBallot,
        o.long("nextResetMs").also { if (it != parsedBallot.endMs) invalid() }, o.bool("changed"),
    )
}

internal fun parseRankingPage(
    value: JsonElement,
    expectedTab: RankingTab,
    expectedType: RankingPeriodType,
): RankingPage {
    val o = value.jsonObject
    if (o.string("tab", 8) != expectedTab.wire) invalid()
    val period = o["period"]?.jsonObject ?: invalid()
    if (period.string("type", 8) != expectedType.wire) invalid()
    val parsedPeriod = RankingPeriod(
        expectedType, period.string("key", 10), period.long("startMs"), period.long("endMs"),
        period.bool("isCurrent"), period.bool("canGoNext"),
    )
    if (parsedPeriod.startMs >= parsedPeriod.endMs) invalid()
    val page = o.int("page").takeIf { it >= 1 } ?: invalid()
    val totalRows = o.int("totalRows")
    val totalPages = o.int("totalPages")
    if (o.int("pageSize", 100) != 10 || totalPages != (totalRows + 9) / 10 || page > maxOf(1, totalPages)) invalid()
    val rows = o["rows"]?.jsonArray ?: invalid()
    if (rows.size > 10) invalid()
    return RankingPage(expectedTab, parsedPeriod, o.long("nextResetMs"), page, totalRows, totalPages,
        rows.mapIndexed { index, raw ->
            val row = raw.jsonObject
            val rank = row.int("rank").takeIf { it == (page - 1) * 10 + index + 1 } ?: invalid()
            RankingRow(rank, track(row), row.int("count").takeIf { it >= 1 } ?: invalid(),
                row.bool("visitorHasMatchingVote"))
        })
}

internal fun parseMyVotesPage(value: JsonElement, expected: MyVotesFilter): MyVotesPage {
    val o = value.jsonObject
    if (o.string("filter", 12) != expected.wire || o.int("pageSize", 100) != 10) invalid()
    val page = o.int("page").takeIf { it >= 1 } ?: invalid()
    val totalRows = o.int("totalRows")
    val totalPages = o.int("totalPages")
    if (totalPages != (totalRows + 9) / 10 || page > maxOf(1, totalPages)) invalid()
    val rows = o["rows"]?.jsonArray ?: invalid()
    if (rows.size > 10) invalid()
    return MyVotesPage(expected, page, totalRows, totalPages, rows.map { raw ->
        val row = raw.jsonObject
        val latest = VoteChoice.from(row.string("latestChoice", 8))?.takeIf { it != VoteChoice.NONE } ?: invalid()
        val current = VoteChoice.from(row.string("currentChoice", 8)) ?: invalid()
        val weekKey = row["currentWeekKey"]?.let { if (it.toString() == "null") null else it.jsonPrimitive.content }
        val weekEnd = row["currentWeekEndMs"]?.let { if (it.toString() == "null") null else it.jsonPrimitive.longOrNull }
        MyVoteRow(track(row), latest, current, weekKey, weekEnd, row.int("likedWeeks"),
            row.int("dislikedWeeks"), row.long("firstVoteMs"), row.long("latestVoteMs"))
    })
}

private fun invalid(): Nothing =
    throw RatingsApiException(502, "invalid_response", "Ratings service returned invalid data")
