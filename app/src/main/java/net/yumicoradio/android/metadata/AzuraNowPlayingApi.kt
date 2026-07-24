// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.metadata

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import net.yumicoradio.android.metadata.model.NowPlaying
import net.yumicoradio.android.metadata.model.RecentTrack
import okhttp3.OkHttpClient
import okhttp3.Request

/** One poll of AzuraCast's now-playing endpoint: current track plus the recent history. */
data class AzuraSnapshot(
    val nowPlaying: NowPlaying,
    val recent: List<RecentTrack>,
)

object AzuraNowPlayingParser {
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    /** Artwork straight from AzuraCast: embedded in the file, so it always matches the track. */
    private fun JsonObject.artUrl(): String? = str("art").ifBlank { null }

    fun parse(raw: String): AzuraSnapshot? = runCatching {
        val root = json.parseToJsonElement(raw).jsonObject
        val np = root["now_playing"]!!.jsonObject
        val song = np["song"]!!.jsonObject
        val listeners = root["listeners"]?.jsonObject
            ?.get("current")?.jsonPrimitive?.intOrNull ?: 0

        val recent = root["song_history"]?.jsonArray.orEmpty().mapNotNull { entry ->
            val e = entry.jsonObject
            val s = e["song"]?.jsonObject ?: return@mapNotNull null
            RecentTrack(
                artist = s.str("artist"),
                title = s.str("title").ifBlank { s.str("text") },
                imageUrl = s.artUrl(),
                uts = e["played_at"]?.jsonPrimitive?.longOrNull,
                playlist = e["playlist"]?.jsonPrimitive?.contentOrNull,
                duration = e["duration"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
            )
        }

        AzuraSnapshot(
            nowPlaying = NowPlaying(
                artist = song.str("artist"),
                title = song.str("title").ifBlank { song.str("text") },
                artworkUrl = song.artUrl(),
                listeners = listeners,
                online = true,
                playedAt = np["played_at"]?.jsonPrimitive?.longOrNull ?: 0L,
                // duration is a float on some entries (182.047347), a whole number on others
                duration = np["duration"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
                playlist = np["playlist"]?.jsonPrimitive?.contentOrNull,
            ),
            recent = recent,
        )
    }.getOrNull()
}

/** Thin HTTP wrapper; all parsing stays in the tested [AzuraNowPlayingParser]. */
class AzuraNowPlayingApi(private val client: OkHttpClient) {
    fun fetch(): AzuraSnapshot? {
        val req = Request.Builder()
            .url("https://yumicoradio.net/api/nowplaying/1")
            .header("Cache-Control", "no-cache")
            .build()
        return client.newCall(req).execute().use { resp ->
            AzuraNowPlayingParser.parse(resp.body?.string().orEmpty())
        }
    }
}
