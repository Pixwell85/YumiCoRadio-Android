package net.yumicoradio.android.playback

enum class StreamQuality(
    val id: String,
    val kbps: Int,
    val url: String,
    val mediaId: String,
) {
    HIGH("high", 256, "https://yumicoradio.net/stream", "live_high"),
    LOW("low", 128, "https://yumicoradio.net/stream_128", "live_low"),
    AAC64("aac64", 64, "https://yumicoradio.net/stream_aac64", "live_aac64");

    companion object {
        val DEFAULT = HIGH
        fun fromId(id: String?): StreamQuality =
            entries.firstOrNull { it.id == id } ?: DEFAULT
        fun fromMediaId(mediaId: String?): StreamQuality =
            entries.firstOrNull { it.mediaId == mediaId } ?: DEFAULT
    }
}
