// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Artist / title / album for an uploaded audio file, read from the sidecar `<url>.tags.json` the
 * station writes next to each upload — the same source the website reads. Only the station's own
 * server is contacted; no third party.
 */
data class AudioTags(val artist: String?, val title: String?, val album: String?) {
    val isEmpty: Boolean get() = artist == null && title == null && album == null

    companion object {
        private fun String?.orNullIfBlank(): String? = this?.trim()?.ifEmpty { null }

        /** Parses the sidecar JSON; null on malformed input or when nothing usable is present. */
        fun parse(json: String): AudioTags? {
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val tags = AudioTags(
                artist = obj.optString("artist").orNullIfBlank(),
                title = obj.optString("title").orNullIfBlank(),
                album = obj.optString("album").orNullIfBlank(),
            )
            return if (tags.isEmpty) null else tags
        }

        /** Fetches and parses `<audioUrl>.tags.json` from the station; null on any failure. */
        suspend fun fetch(http: OkHttpClient, audioUrl: String): AudioTags? = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url("$audioUrl.tags.json").build()).execute().use { resp ->
                    if (!resp.isSuccessful) null else resp.body?.string()?.let { parse(it) }
                }
            }.getOrNull()
        }
    }
}
