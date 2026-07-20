package net.yumicoradio.android.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The VU meter's arithmetic, tested without a player or a canvas.
 *
 * What is *not* testable here, and is judged on device: whether [VuScale.FLOOR_DB] puts real music
 * where a VU meter should sit, and whether the segments look right.
 */
class VuScaleTest {

    /** Interleaves [channels] channels of 16-bit PCM from per-channel sample generators. */
    private fun pcm(frames: Int, channels: Int, sample: (channel: Int, frame: Int) -> Double):
        ByteBuffer {
        val buf = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                buf.putShort((sample(c, f) * 32767).toInt().toShort())
            }
        }
        buf.rewind()
        return buf
    }

    @Test
    fun `silence reads zero`() {
        val buf = pcm(frames = 512, channels = 2) { _, _ -> 0.0 }
        assertEquals(0f, VuScale.rms16(buf, channelCount = 2, channel = 0))
    }

    /** A full-scale sine has an RMS of 1/sqrt(2). Getting this wrong is how a meter ends up half-lit. */
    @Test
    fun `a full-scale sine reads its true RMS`() {
        val buf = pcm(frames = 4096, channels = 1) { _, f -> sin(2 * Math.PI * f / 64) }
        val rms = VuScale.rms16(buf, channelCount = 1, channel = 0)
        assertTrue(abs(rms - 0.707f) < 0.01f, "expected ~0.707, got $rms")
    }

    /**
     * The channels must be read apart, not averaged. A stereo buffer with a loud left and a silent
     * right is exactly what a broken de-interleave turns into two identical half-lit meters.
     */
    @Test
    fun `channels are de-interleaved, not mixed`() {
        val buf = pcm(frames = 2048, channels = 2) { c, f ->
            if (c == 0) sin(2 * Math.PI * f / 64) else 0.0
        }
        val left = VuScale.rms16(buf, channelCount = 2, channel = 0)
        val right = VuScale.rms16(buf, channelCount = 2, channel = 1)

        assertTrue(abs(left - 0.707f) < 0.01f, "left should be full: $left")
        assertEquals(0f, right, "right should be silent")
        assertNotEquals(left, right)
    }

    /** Reading the buffer must not consume it — the audio pipeline still has to play it. */
    @Test
    fun `reading the level leaves the buffer untouched`() {
        val buf = pcm(frames = 256, channels = 2) { _, f -> sin(f.toDouble()) }
        val positionBefore = buf.position()
        val limitBefore = buf.limit()

        VuScale.rms16(buf, channelCount = 2, channel = 0)

        assertEquals(positionBefore, buf.position(), "position moved")
        assertEquals(limitBefore, buf.limit(), "limit moved")
    }

    /**
     * The regression that made beta29 unusable: a broadcast stream is compressed and
     * loudness-normalised, so its RMS sits high and barely moves. On the linear scale beta29 used it
     * pinned the meter to the red permanently.
     */
    @Test
    fun `a loud broadcast level reaches the red without pinning there`() {
        // ~-9 dBFS RMS: a normal loud passage on a normalised radio stream.
        val loud = VuScale.levelFromRms(0.355f)
        assertTrue(loud in 0.8f..0.99f, "a loud passage should reach the red, not sit under it: $loud")
        assertEquals(
            VuScale.Zone.PEAK, VuScale.zoneOf(VuScale.litSegments(loud) - 1),
            "the top lit segment on loud material should be in the red",
        )
        assertTrue(
            VuScale.litSegments(loud) < VuScale.SEGMENTS,
            "but it must keep a little headroom for transients",
        )
    }

    /**
     * beta30's fault: the scale topped out at digital full scale, which real music never reaches, so
     * the red was decorative. The top of the ladder must be a level that actually occurs.
     */
    @Test
    fun `the top of the scale is reachable by real material`() {
        // A transient peaking near -3 dBFS must light the whole ladder.
        assertEquals(1f, VuScale.levelFromRms(0.71f), "a peak near full scale must light everything")
    }

    @Test
    fun `the scale spreads quiet and loud apart`() {
        val quiet = VuScale.levelFromRms(0.02f)   // -34 dBFS
        val loud = VuScale.levelFromRms(0.355f)   // -9 dBFS
        assertTrue(quiet > 0f, "audible but quiet music should still light the meter: $quiet")
        assertTrue(loud - quiet > 0.2f, "quiet and loud must be visibly apart: $quiet vs $loud")
    }

    @Test
    fun `full scale clamps, and silence lights nothing`() {
        assertEquals(1f, VuScale.levelFromRms(1f))
        assertEquals(1f, VuScale.levelFromRms(2f), "an over must clamp rather than overflow")
        assertEquals(0f, VuScale.levelFromRms(0f))
    }

    /**
     * The tap sits before the player applies its own volume, so the level has to be scaled here.
     * Without this the meter read identically at full volume and at silence.
     */
    @Test
    fun `the app's volume moves the meter`() {
        val full = VuScale.levelFromRms(0.355f, volume = 1f)
        val half = VuScale.levelFromRms(0.355f, volume = 0.5f)
        val muted = VuScale.levelFromRms(0.355f, volume = 0f)

        assertTrue(half < full, "halving the volume must lower the meter: $half vs $full")
        assertEquals(0f, muted, "a muted player must show a silent meter")
    }

    @Test
    fun `the meter rises faster than it falls`() {
        val rising = VuScale.smooth(previous = 0f, target = 1f)
        val falling = VuScale.smooth(previous = 1f, target = 0f)
        assertTrue(rising > 1f - falling, "attack ($rising) should outpace release (${1f - falling})")
    }

    @Test
    fun `lit segments follow the level and never overflow`() {
        assertEquals(0, VuScale.litSegments(0f))
        assertEquals(16, VuScale.litSegments(0.5f))
        assertEquals(32, VuScale.litSegments(1f))
        assertEquals(32, VuScale.litSegments(2f), "a level above full scale must not draw off the end")
    }

    /** The site turns amber at 60% and red at 80% (js/yumiPlayer.js:581). */
    @Test
    fun `colour zones change where the site changes them`() {
        assertEquals(VuScale.Zone.NORMAL, VuScale.zoneOf(0))
        assertEquals(VuScale.Zone.NORMAL, VuScale.zoneOf(19))   // 19/32 = 0.59
        assertEquals(VuScale.Zone.WARN, VuScale.zoneOf(20))     // 20/32 = 0.62
        assertEquals(VuScale.Zone.WARN, VuScale.zoneOf(25))     // 25/32 = 0.78
        assertEquals(VuScale.Zone.PEAK, VuScale.zoneOf(26))     // 26/32 = 0.81
        assertEquals(VuScale.Zone.PEAK, VuScale.zoneOf(31))
    }

    @Test
    fun `the peak marker snaps up, holds, then falls`() {
        var peak = VuScale.Peak().step(0.8f)
        assertEquals(0.8f, peak.value, "a new high is taken immediately")

        repeat(VuScale.PEAK_HOLD_FRAMES) { peak = peak.step(0.1f) }
        assertEquals(0.8f, peak.value, "the marker must sit still for the whole hold")

        peak = peak.step(0.1f)
        assertTrue(peak.value < 0.8f, "the marker should start falling once the hold expires")
    }

    @Test
    fun `the peak marker cannot fall below zero`() {
        var peak = VuScale.Peak(value = 0.02f, hold = 0)
        repeat(100) { peak = peak.step(0f) }
        assertEquals(0f, peak.value)
    }

    @Test
    fun `no peak marker is drawn when there is nothing to mark`() {
        assertNull(VuScale.peakSegment(0f), "silence should not leave a marker sitting at zero")
        assertEquals(31, VuScale.peakSegment(1f), "a full peak marks the last segment, not past it")
    }
}
