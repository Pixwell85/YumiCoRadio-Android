package net.yumicoradio.android.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SleepTimerTest {
    @Test fun reports_remaining_and_fires_once_at_zero() {
        var now = 0L
        var fired = 0
        val t = SleepTimer(nowMs = { now })
        t.start(durationMs = 30_000) { fired++ }
        assertTrue(t.isActive)
        assertEquals(30_000, t.remainingMs())
        now = 10_000; assertEquals(20_000, t.remainingMs()); t.tick()
        assertEquals(0, fired)
        now = 30_000; t.tick()
        assertEquals(1, fired)
        assertFalse(t.isActive)
        now = 40_000; t.tick()             // no double-fire
        assertEquals(1, fired)
    }
    @Test fun cancel_stops_it() {
        var now = 0L
        var fired = 0
        val t = SleepTimer(nowMs = { now })
        t.start(60_000) { fired++ }
        t.cancel()
        assertFalse(t.isActive)
        now = 60_000; t.tick()
        assertEquals(0, fired)
    }
}
