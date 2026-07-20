package net.yumicoradio.android.util

/**
 * Elapsed/duration readout for the status bar.
 *
 * The payload's own `elapsed` is only correct at the instant of the poll, so elapsed is derived
 * from `played_at` instead and clamped — the same correction the website makes.
 */
object TrackTime {

    /** Returns "1:17 / 2:48", or null when there is no finite track to count (e.g. a live DJ set). */
    fun label(playedAt: Long, duration: Int, nowSeconds: Long): String? {
        if (playedAt <= 0L || duration <= 0) return null
        val elapsed = (nowSeconds - playedAt).coerceIn(0L, duration.toLong())
        return "${mmss(elapsed)} / ${mmss(duration.toLong())}"
    }

    private fun mmss(seconds: Long): String =
        "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
