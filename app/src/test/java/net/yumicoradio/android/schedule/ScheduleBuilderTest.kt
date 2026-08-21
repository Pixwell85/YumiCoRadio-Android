// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.schedule

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleBuilderTest {

    /** 12:00:00 UTC on an arbitrary day, so the hour under test runs 12:00 → 13:00. */
    private val hourStart = 1_784_390_400L
    private val hourEnd = hourStart + 3600

    private fun entry(program: Program, offsetMin: Int, durationMin: Int) =
        ScheduleEntry(
            program = program,
            startedAt = hourStart + offsetMin * 60,
            duration = durationMin * 60,
        )

    @Test
    fun `an empty timeline produces no blocks`() {
        assertTrue(ScheduleBuilder.blocksForHour(emptyList(), hourStart).isEmpty())
    }

    @Test
    fun `a single track fills its own span and the rest of the hour`() {
        val blocks = ScheduleBuilder.blocksForHour(listOf(entry(Program.CITYPOP, 0, 10)), hourStart)
        // One programme all hour: the gap after it is filled with the same programme rather than
        // leaving a hole, because the station keeps playing.
        assertEquals(1, blocks.size)
        assertEquals(Program.CITYPOP, blocks[0].program)
        assertEquals(hourStart, blocks[0].start)
        assertEquals(hourEnd, blocks[0].end)
    }

    @Test
    fun `consecutive tracks of the same programme merge into one block`() {
        val blocks = ScheduleBuilder.blocksForHour(
            listOf(
                entry(Program.VAPORWAVE, 0, 10),
                entry(Program.VAPORWAVE, 10, 10),
                entry(Program.VAPORWAVE, 20, 10),
            ),
            hourStart,
        )
        assertEquals(1, blocks.size)
        assertEquals(Program.VAPORWAVE, blocks[0].program)
    }

    @Test
    fun `a programme change starts a new block`() {
        val blocks = ScheduleBuilder.blocksForHour(
            listOf(
                entry(Program.CITYPOP, 0, 30),
                entry(Program.VAPORWAVE, 30, 30),
            ),
            hourStart,
        )
        assertEquals(listOf(Program.CITYPOP, Program.VAPORWAVE), blocks.map { it.program })
        assertEquals(hourStart + 30 * 60, blocks[1].start)
        assertEquals(hourEnd, blocks[1].end)
    }

    @Test
    fun `the hour is covered end to end with no gaps`() {
        val blocks = ScheduleBuilder.blocksForHour(
            listOf(
                entry(Program.FUTUREFUNK, 5, 10),
                entry(Program.CITYPOP, 25, 10),
            ),
            hourStart,
        )
        assertEquals(hourStart, blocks.first().start, "the hour must start covered")
        assertEquals(hourEnd, blocks.last().end, "the hour must end covered")
        blocks.zipWithNext().forEach { (a, b) ->
            assertEquals(a.end, b.start, "a gap was left between blocks")
        }
    }

    /** Tracks straddling the hour boundary are clipped: this view is one hour, not a timeline. */
    @Test
    fun `tracks are clipped to the hour`() {
        val blocks = ScheduleBuilder.blocksForHour(
            listOf(
                ScheduleEntry(Program.CITYPOP, hourStart - 1800, 3600),
                ScheduleEntry(Program.VAPORWAVE, hourStart + 1800, 7200),
            ),
            hourStart,
        )
        assertEquals(hourStart, blocks.first().start)
        assertEquals(hourEnd, blocks.last().end)
        assertTrue(blocks.all { it.start >= hourStart && it.end <= hourEnd })
    }

    @Test
    fun `entries entirely outside the hour are ignored`() {
        val blocks = ScheduleBuilder.blocksForHour(
            listOf(
                ScheduleEntry(Program.CITYPOP, hourStart - 7200, 600),
                ScheduleEntry(Program.VAPORWAVE, hourStart + 600, 600),
            ),
            hourStart,
        )
        assertTrue(blocks.all { it.program == Program.VAPORWAVE })
    }

    @Test
    fun `entries are sorted before building, whatever order they arrive in`() {
        val blocks = ScheduleBuilder.blocksForHour(
            listOf(
                entry(Program.VAPORWAVE, 30, 30),
                entry(Program.CITYPOP, 0, 30),
            ),
            hourStart,
        )
        assertEquals(listOf(Program.CITYPOP, Program.VAPORWAVE), blocks.map { it.program })
    }

    @Test
    fun `overlapping source entries keep every programme transition visible`() {
        val blocks = ScheduleBuilder.blocksForHour(
            listOf(
                entry(Program.CITYPOP, 0, 6),
                entry(Program.FUTUREFUNK, 5, 27),
                entry(Program.VAPORWAVE, 30, 4),
                entry(Program.FUTUREFUNK, 33, 27),
            ),
            hourStart,
        )

        assertEquals(
            listOf(Program.CITYPOP, Program.FUTUREFUNK, Program.VAPORWAVE, Program.FUTUREFUNK),
            blocks.map { it.program },
        )
        assertTrue(blocks.all { it.end > it.start }, "an overlapping entry became invisible")
        blocks.zipWithNext().forEach { (a, b) ->
            assertEquals(a.end, b.start, "overlap normalization left a gap or overlap")
        }
    }

    @Test
    fun `fractions place a block across the hour`() {
        val block = ScheduleBlock(Program.CITYPOP, hourStart + 900, hourStart + 1800)
        assertEquals(0.25f, block.startFraction(hourStart))
        assertEquals(0.25f, block.widthFraction(hourStart))
    }

    @Test
    fun `the playhead is where the clock says`() {
        assertEquals(0f, ScheduleBuilder.playheadFraction(hourStart, hourStart))
        assertEquals(0.5f, ScheduleBuilder.playheadFraction(hourStart + 1800, hourStart))
        // Clamped: a clock that has run past the hour must not draw off the end of the bar.
        assertEquals(1f, ScheduleBuilder.playheadFraction(hourStart + 7200, hourStart))
    }

    @Test
    fun `playlist names map to programmes as the site maps them`() {
        assertEquals(Program.CITYPOP, Program.fromPlaylist("CityPop"))
        assertEquals(Program.CITYPOP, Program.fromPlaylist("ADS"))
        assertEquals(Program.VAPORWAVE, Program.fromPlaylist("Vaporwave"))
        assertEquals(Program.FUTUREFUNK, Program.fromPlaylist("Yumi"))
        assertEquals(Program.FUTUREFUNK, Program.fromPlaylist("anything else"))
        assertEquals(Program.FUTUREFUNK, Program.fromPlaylist(null))
        // The site compares loosely; a different case must not silently become Future Funk.
        assertEquals(Program.VAPORWAVE, Program.fromPlaylist("vaporwave"))
    }
}

/**
 * The station's fixed grid: City Pop on the hour, Vaporwave at half past, Future Funk between.
 *
 * Without this the Coming Up box goes blank the moment the last transition of the hour has passed,
 * which is most of the hour.
 */
class NextSlotTest {

    private val hourStart = 1_784_390_400L

    @Test
    fun `before half past, the next slot is vaporwave at thirty`() {
        val slot = ScheduleBuilder.nextSlot(hourStart + 10 * 60)
        assertEquals(Program.VAPORWAVE, slot.program)
        assertEquals(hourStart + 30 * 60, slot.start)
    }

    @Test
    fun `after half past, the next slot is city pop on the next hour`() {
        val slot = ScheduleBuilder.nextSlot(hourStart + 40 * 60)
        assertEquals(Program.CITYPOP, slot.program)
        assertEquals(hourStart + 3600, slot.start)
    }

    @Test
    fun `exactly on the half hour looks ahead, not at itself`() {
        val slot = ScheduleBuilder.nextSlot(hourStart + 30 * 60)
        assertEquals(Program.CITYPOP, slot.program)
        assertEquals(hourStart + 3600, slot.start)
    }

    @Test
    fun `exactly on the hour points at half past`() {
        val slot = ScheduleBuilder.nextSlot(hourStart)
        assertEquals(Program.VAPORWAVE, slot.program)
        assertEquals(hourStart + 30 * 60, slot.start)
    }
}

/**
 * What comes next, with its end — the Coming Up box shows a range like Now Playing does.
 *
 * Computed on unclipped entries: clipping to the hour would report a slot starting at 23:59 as
 * ending at midnight, which is the clipping showing through rather than the truth.
 */
class UpcomingTest {

    private val hourStart = 1_784_390_400L

    private fun entry(program: Program, offsetMin: Int, durationMin: Int) =
        ScheduleEntry(program, hourStart + offsetMin * 60, durationMin * 60)

    @Test
    fun `reports the next different programme and how long it runs`() {
        val slot = ScheduleBuilder.upcoming(
            listOf(
                entry(Program.FUTUREFUNK, 0, 10),
                entry(Program.CITYPOP, 10, 4),
                entry(Program.FUTUREFUNK, 14, 10),
            ),
            now = hourStart + 5 * 60,
        )
        assertEquals(Program.CITYPOP, slot.program)
        assertEquals(hourStart + 10 * 60, slot.start)
        assertEquals(hourStart + 14 * 60, slot.end)
    }

    @Test
    fun `consecutive tracks of the upcoming programme extend its end`() {
        val slot = ScheduleBuilder.upcoming(
            listOf(
                entry(Program.FUTUREFUNK, 0, 5),
                entry(Program.VAPORWAVE, 5, 3),
                entry(Program.VAPORWAVE, 8, 3),
                entry(Program.FUTUREFUNK, 11, 5),
            ),
            now = hourStart,
        )
        assertEquals(Program.VAPORWAVE, slot.program)
        assertEquals(hourStart + 11 * 60, slot.end)
    }

    /** A slot that runs past the hour keeps its real end, not midnight. */
    @Test
    fun `an upcoming slot is not clipped to the hour`() {
        val slot = ScheduleBuilder.upcoming(
            listOf(
                entry(Program.FUTUREFUNK, 0, 59),
                entry(Program.CITYPOP, 59, 5),
            ),
            now = hourStart,
        )
        assertEquals(hourStart + 64 * 60, slot.end)
    }

    @Test
    fun `with nothing ahead it falls back to the fixed grid`() {
        val slot = ScheduleBuilder.upcoming(emptyList(), now = hourStart + 10 * 60)
        assertEquals(Program.VAPORWAVE, slot.program)
        assertEquals(hourStart + 30 * 60, slot.start)
        // No end is known from the grid alone, so none is claimed.
        assertEquals(null, slot.end)
    }
}
