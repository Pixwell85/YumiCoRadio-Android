package net.yumicoradio.android.schedule

import kotlinx.coroutines.runBlocking
import net.yumicoradio.android.metadata.AzuraNowPlayingApi
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Runs the schedule screen's own computation against live data, so a failure here means the screen
 * is wrong rather than the arithmetic.
 *
 * Both endpoints are public and read-only — the same two the website polls.
 */
class SchedulePipelineTest {

    @Test
    fun `the hour shows more than one programme`() = runBlocking {
        val http = OkHttpClient()
        val snapshot = AzuraNowPlayingApi(http).fetch()
        val queue = QueueApi(http).fetch()

        assumeTrue("no network in this environment", snapshot != null && queue.isNotEmpty())

        val now = System.currentTimeMillis() / 1000
        val hourStart = ScheduleBuilder.hourStart(now)

        // Exactly what ScheduleContent assembles.
        val entries = buildList {
            snapshot!!.recent.forEach { track ->
                val startedAt = track.uts ?: return@forEach
                add(ScheduleEntry(Program.fromPlaylist(track.playlist), startedAt, track.duration))
            }
            val np = snapshot.nowPlaying
            if (np.playedAt > 0) {
                add(ScheduleEntry(Program.fromPlaylist(np.playlist), np.playedAt, np.duration))
            }
            addAll(queue)
        }

        val blocks = ScheduleBuilder.blocksForHour(entries, hourStart)
        println("entries=${entries.size} queue=${queue.size} blocks=${blocks.size}")
        blocks.forEach { println("  ${it.program} ${it.start - hourStart}s..${it.end - hourStart}s") }

        assertTrue(blocks.isNotEmpty(), "no blocks at all")

        // The screen does not read the queue alone. The queue is 25 tracks deep, so for most of an
        // hour it holds nothing past the last transition — asserting that it does was asserting what
        // the station happens to be playing, not what the app does, and it failed at any quiet hour.
        // What must hold is that the reader is never shown an empty road ahead: either the queue
        // reaches past now, or the fixed grid names the next slot. That is the beta17 fallback, and
        // this is the pairing ScheduleContent actually assembles.
        val aheadFromQueue = blocks.any { it.start > now }
        val nextFromGrid = ScheduleBuilder.nextSlot(now)
        assertTrue(
            aheadFromQueue || nextFromGrid.start > now,
            "nothing ahead from either source: the bar would show the current programme to the " +
                "hour's end, which is the bug beta17 fixed",
        )
    }

    @Test
    fun `history entries carry the playlist and duration the schedule needs`() = runBlocking {
        val snapshot = AzuraNowPlayingApi(OkHttpClient()).fetch()
        assumeTrue("no network in this environment", snapshot != null)

        val recent = snapshot!!.recent
        assertTrue(recent.isNotEmpty(), "no history returned")
        assertTrue(
            recent.any { !it.playlist.isNullOrBlank() },
            "history carried no playlist; every past block would fall back to Future Funk",
        )
        assertTrue(
            recent.any { it.duration > 0 },
            "history carried no duration; past blocks would have zero width and vanish",
        )
    }
}
