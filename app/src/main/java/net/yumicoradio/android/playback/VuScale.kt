package net.yumicoradio.android.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The arithmetic behind the VU meter, kept apart from both the audio pipeline and the canvas so it
 * can be tested without either.
 *
 * The website's meter (`js/yumiPlayer.js:528`) is the reference for the segment layout and the peak
 * behaviour. Its *level* is not portable: it derives one from FFT bins and then multiplies by a
 * stereo ratio and a fudge factor of 2. Here the level is a true per-channel RMS, so the calibration
 * ([GAIN]) is this app's own.
 */
object VuScale {

    /** Segments per channel, as the site draws. */
    const val SEGMENTS = 32

    /** Frames the peak marker stays put before it starts falling. */
    const val PEAK_HOLD_FRAMES = 30

    /** How far the peak marker falls per frame once the hold expires. */
    const val PEAK_DECAY = 0.01f

    /**
     * The bottom of the scale, in dBFS. Anything quieter than this lights nothing.
     *
     * The meter is graduated in decibels, not in raw amplitude. A linear scale multiplied by a gain
     * — which is what beta29 shipped — pins a radio stream to the top and holds it there: broadcast
     * audio is compressed and loudness-normalised, so its RMS barely moves and sits high. In dB the
     * same signal spreads out across the ladder and only transients reach the red.
     */
    const val FLOOR_DB = -48f

    /**
     * The top of the scale, in dBFS — the reading that lights the last segment.
     *
     * Not 0. A meter whose top is digital full scale never reaches its own red on real material,
     * which is what beta30 did: broadcast audio peaks around -3 and sits near -9, so the last
     * segments were unreachable. Studio meters do the same thing, calibrating their top below full
     * scale and keeping the rest as headroom.
     */
    const val TOP_DB = -6f

    /** Jump to a rise immediately, fall back slowly: a meter that decays as fast as it rises reads as noise. */
    const val ATTACK = 0.55f
    const val RELEASE = 0.12f

    enum class Zone { NORMAL, WARN, PEAK }

    /** RMS of one channel of interleaved 16-bit PCM, as a 0..1 fraction of full scale. */
    fun rms16(buffer: ByteBuffer, channelCount: Int, channel: Int): Float {
        if (channelCount <= 0 || channel >= channelCount) return 0f
        val view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sum = 0.0
        var count = 0
        var i = channel
        while (i < view.limit()) {
            val sample = view.get(i) / 32768.0
            sum += sample * sample
            count++
            i += channelCount
        }
        return if (count == 0) 0f else sqrt(sum / count).toFloat()
    }

    /**
     * Turns an RMS reading into a position on the ladder, in decibels, scaled by [volume].
     *
     * [volume] is the *app's* own level (0..1). The tap sits before the player applies it, so
     * without this the meter reads the same whether the app is silent or at full — which is what
     * made it look stuck at maximum. The device's own volume is deliberately not consulted: it is
     * not ours to read, and the meter describes what this player is sending out.
     */
    fun levelFromRms(rms: Float, volume: Float = 1f): Float {
        val scaled = rms * volume.coerceIn(0f, 1f)
        if (scaled <= 0f) return 0f
        val db = 20f * log10(scaled)
        return ((db - FLOOR_DB) / (TOP_DB - FLOOR_DB)).coerceIn(0f, 1f)
    }

    /** Fast attack, slow release. */
    fun smooth(previous: Float, target: Float): Float {
        val factor = if (target > previous) ATTACK else RELEASE
        return previous + (target - previous) * factor
    }

    /** How many segments are lit at [level]. */
    fun litSegments(level: Float, segments: Int = SEGMENTS): Int =
        (level.coerceIn(0f, 1f) * segments).toInt().coerceAtMost(segments)

    /**
     * The colour band a segment falls in: the site turns amber at 60% of the scale and red at 80%
     * (`js/yumiPlayer.js:581`).
     */
    fun zoneOf(index: Int, segments: Int = SEGMENTS): Zone {
        val t = index.toFloat() / segments
        return when {
            t < 0.6f -> Zone.NORMAL
            t < 0.8f -> Zone.WARN
            else -> Zone.PEAK
        }
    }

    /** Which segment carries the peak marker, or null while there is nothing worth marking. */
    fun peakSegment(peak: Float, segments: Int = SEGMENTS): Int? =
        if (peak <= 0.01f) null
        else (peak * segments).toInt().coerceIn(0, segments - 1)

    /**
     * The peak marker: it snaps up to any new high, sits for [PEAK_HOLD_FRAMES], then slides down.
     * Immutable so the state machine can be stepped in a test without a running player.
     */
    data class Peak(val value: Float = 0f, val hold: Int = 0) {
        fun step(level: Float): Peak = when {
            level >= value -> Peak(level, PEAK_HOLD_FRAMES)
            hold > 0 -> Peak(value, hold - 1)
            else -> Peak(max(0f, value - PEAK_DECAY), 0)
        }
    }
}
