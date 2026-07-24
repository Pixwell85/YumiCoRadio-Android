// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamQualityTest {
    @Test fun urls_are_https_and_correct() {
        assertEquals("https://yumicoradio.net/stream", StreamQuality.HIGH.url)
        assertEquals("https://yumicoradio.net/stream_128", StreamQuality.LOW.url)
        assertEquals("https://yumicoradio.net/stream_aac64", StreamQuality.AAC64.url)
    }
    @Test fun kbps_are_correct() {
        assertEquals(256, StreamQuality.HIGH.kbps)
        assertEquals(128, StreamQuality.LOW.kbps)
        assertEquals(64, StreamQuality.AAC64.kbps)
    }
    @Test fun default_is_high_256() {
        assertEquals(StreamQuality.HIGH, StreamQuality.DEFAULT)
    }
    @Test fun round_trips_by_id() {
        assertEquals(StreamQuality.LOW, StreamQuality.fromId("low"))
        assertEquals(StreamQuality.AAC64, StreamQuality.fromId("aac64"))
        assertEquals(StreamQuality.HIGH, StreamQuality.fromId("nonsense")) // fallback default
    }
    @Test fun round_trips_by_media_id() {
        assertEquals(StreamQuality.HIGH, StreamQuality.fromMediaId("live_high"))
        assertEquals(StreamQuality.LOW, StreamQuality.fromMediaId("live_low"))
        assertEquals(StreamQuality.AAC64, StreamQuality.fromMediaId("live_aac64"))
        assertEquals(StreamQuality.HIGH, StreamQuality.fromMediaId(null)) // fallback default
    }
    @Test fun media_ids_are_unique() {
        assertEquals(StreamQuality.entries.size, StreamQuality.entries.map { it.mediaId }.toSet().size)
    }
}
