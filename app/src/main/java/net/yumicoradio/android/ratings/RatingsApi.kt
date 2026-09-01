// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ratings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

class RatingsApi(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://s1.yumicoradio.net/ratings",
) {
    suspend fun currentVote(timezone: String, bearer: String?, voterToken: String): CurrentVote =
        parseCurrentVote(request("/v1/current-vote?timezone=${encode(timezone)}", bearer = bearer, voter = voterToken))

    suspend fun putVote(
        current: CurrentVote,
        choice: VoteChoice,
        timezone: String,
        bearer: String?,
        voterToken: String,
    ): CurrentVote = parseCurrentVote(request("/v1/current-vote", "PUT", buildJsonObject {
        put("trackId", current.track.trackId)
        put("occurrenceId", current.occurrenceId)
        put("timezone", timezone)
        put("choice", choice.wire)
    }, bearer, voterToken))

    suspend fun rankings(
        tab: RankingTab,
        type: RankingPeriodType,
        anchor: String,
        page: Int,
        timezone: String,
        bearer: String?,
        voterToken: String,
    ): RankingPage = parseRankingPage(request(
        "/v1/rankings?tab=${tab.wire}&type=${type.wire}&anchor=${encode(anchor)}" +
            "&timezone=${encode(timezone)}&page=$page",
        bearer = bearer, voter = voterToken,
    ), tab, type)

    suspend fun myVotes(
        filter: MyVotesFilter,
        page: Int,
        bearer: String?,
        voterToken: String,
    ): MyVotesPage = parseMyVotesPage(request(
        "/v1/account/my-votes?filter=${filter.wire}&page=$page",
        bearer = bearer,
        voter = voterToken,
    ), filter)

    suspend fun mergeAnonymous(bearer: String, voterToken: String) {
        request("/v1/account/merge-anonymous", "POST", buildJsonObject {}, bearer, voterToken)
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: JsonObject? = null,
        bearer: String? = null,
        voter: String? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path).header("Accept", "application/json")
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        if (voter != null) builder.header("X-YCR-Voter-Token", voter)
        val requestBody = (body ?: buildJsonObject {}).toString().toRequestBody(JSON_MEDIA)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody)
            "PUT" -> builder.put(requestBody)
            else -> error("Unsupported method")
        }
        val response = try { http.newCall(builder.build()).execute() }
        catch (_: IOException) { throw RatingsApiException(0, "network_error", "Could not reach ratings service") }
        response.use {
            val declared = it.body?.contentLength() ?: 0
            if (declared > MAX_BYTES) invalid()
            val text = it.body?.string().orEmpty()
            if (text.length > MAX_BYTES) invalid()
            val json = if (text.isBlank()) JsonNull else runCatching { JSON.parseToJsonElement(text) }.getOrElse { invalid() }
            if (!it.isSuccessful) {
                val error = (json as? JsonObject)?.get("error") as? JsonObject
                val code = error?.get("code")?.jsonPrimitive?.content?.take(64) ?: "request_failed"
                throw RatingsApiException(it.code, code, safeMessage(code))
            }
            json
        }
    }

    private fun safeMessage(code: String): String = mapOf(
        "rate_limited" to "Too many requests. Please try again later",
        "now_playing_unavailable" to "Current track is not available for voting",
        "track_transition" to "Track changed. Please vote again",
        "account_session_invalid" to "Account session expired",
        "identity_unavailable" to "Account service unavailable",
        "anonymous_voter_merged" to "Local votes were already synchronized",
        "merge_unavailable" to "Vote synchronization unavailable",
        "service_unavailable" to "Ratings service unavailable",
    )[code] ?: "Ratings request failed"

    private fun invalid(): Nothing = throw RatingsApiException(502, "invalid_response", "Ratings service returned invalid data")
    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = false }
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val MAX_BYTES = 262_144
    }
}
