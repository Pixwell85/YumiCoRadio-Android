package net.yumicoradio.android.chat

/**
 * Finds links in a message and says what they point at, classifying by extension exactly as the
 * website does so both clients decide to preview the same things.
 */
object MediaLinks {

    enum class Kind { IMAGE, AUDIO, VIDEO, FILE, LINK }

    data class Link(val url: String, val kind: Kind) {
        /** Uploads come from the chat server's own uploads directory. */
        val isUpload: Boolean get() = "/chat/uploads/" in url
    }

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
            .map { url -> Link(url, kindOf(url)) }
            .toList()

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
