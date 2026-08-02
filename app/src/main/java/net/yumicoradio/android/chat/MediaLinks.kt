// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * Finds links in a message and says what they point at, classifying by extension exactly as the
 * website does so both clients decide to preview the same things.
 */
object MediaLinks {

    enum class Kind { IMAGE, AUDIO, VIDEO, FILE, LINK }

    /**
     * A recognised sharing platform. `glyph` is a W95FA-safe geometric char (`▶`/`♪`) or empty for
     * image hosts — no colour emoji, which the Win98 font cannot render. Detection mirrors the
     * website's `createEmbed` URL matching exactly so both clients recognise the same links.
     */
    enum class Platform(val label: String, val glyph: String) {
        YOUTUBE("YouTube", "▶"),
        SOUNDCLOUD("SoundCloud", "♪"),
        SPOTIFY("Spotify", "♪"),
        BANDCAMP("Bandcamp", "♪"),
        VIMEO("Vimeo", "▶"),
        DAILYMOTION("Dailymotion", "▶"),
        TWITCH("Twitch", "▶"),
        STREAMABLE("Streamable", "▶"),
        IMGUR("Imgur", ""),
        GYAZO("Gyazo", ""),
    }

    data class Link(val url: String, val kind: Kind, val platform: Platform? = null) {
        /** Uploads come from the chat server's own uploads directory. */
        val isUpload: Boolean get() = "/chat/uploads/" in url
    }

    /** A URL's character range inside a piece of text, with trailing punctuation already trimmed. */
    data class Span(val start: Int, val end: Int, val url: String)

    private val URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    // Sentence punctuation sits against a URL far more often than it belongs to one.
    private const val TRAILING = ".,;:!?)]}>\"'"

    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp")
    private val AUDIO_EXT = setOf("mp3", "wav", "ogg", "flac", "aac", "weba", "m4a")
    private val VIDEO_EXT = setOf("mp4", "webm", "ogv", "mkv", "mov")
    private val FILE_EXT = setOf("pdf")

    fun find(text: String): List<Link> =
        URL.findAll(text)
            .map { it.value.trimEnd { ch -> ch in TRAILING } }
            .filter { it.isNotEmpty() }
            .map { url ->
                val kind = kindOf(url)
                Link(url, kind, if (kind == Kind.LINK) platformOf(url) else null)
            }
            .toList()

    /** URL character ranges within [text], used to make the message's own text tappable. */
    fun spans(text: String): List<Span> =
        URL.findAll(text).mapNotNull { m ->
            val trimmed = m.value.trimEnd { ch -> ch in TRAILING }
            if (trimmed.isEmpty()) null
            else Span(m.range.first, m.range.first + trimmed.length, trimmed)
        }.toList()

    private fun platformOf(url: String): Platform? {
        val u = url.lowercase()
        return when {
            "youtube.com/watch?v=" in u || "youtu.be/" in u -> Platform.YOUTUBE
            "soundcloud.com/" in u -> Platform.SOUNDCLOUD
            "open.spotify.com/" in u -> Platform.SPOTIFY
            "bandcamp.com/track/" in u || "bandcamp.com/album/" in u -> Platform.BANDCAMP
            "vimeo.com/" in u -> Platform.VIMEO
            "dailymotion.com/video/" in u || "dai.ly/" in u -> Platform.DAILYMOTION
            "twitch.tv/" in u -> Platform.TWITCH
            "streamable.com/" in u -> Platform.STREAMABLE
            "imgur.com/" in u && "i.imgur.com" !in u -> Platform.IMGUR
            "gyazo.com/" in u && "i.gyazo.com" !in u -> Platform.GYAZO
            else -> null
        }
    }

    private fun kindOf(url: String): Kind {
        // Query strings and fragments must not hide the extension.
        val path = url.substringBefore('?').substringBefore('#')
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            in IMAGE_EXT -> Kind.IMAGE
            in AUDIO_EXT -> Kind.AUDIO
            in VIDEO_EXT -> Kind.VIDEO
            in FILE_EXT -> Kind.FILE
            else -> Kind.LINK
        }
    }
}
