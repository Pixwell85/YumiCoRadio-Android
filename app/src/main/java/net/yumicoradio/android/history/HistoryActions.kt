// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.history

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object HistoryActions {
    fun displayText(artist: String, title: String): String = "$artist - $title"

    fun youtubeUrl(query: String): String =
        "https://www.youtube.com/results?search_query=${queryComponent(query)}"

    fun googleUrl(query: String): String =
        "https://www.google.com/search?q=${queryComponent(query)}"

    fun spotifyUrl(query: String): String =
        "https://open.spotify.com/search/${queryComponent(query).replace("+", "%20")}"

    private fun queryComponent(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
