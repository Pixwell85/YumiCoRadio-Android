// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.schedule

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleTimelineTest {

    private val hourStart = 1_784_390_400L

    private fun entry(program: Program, offsetMin: Int, durationMin: Int = 3) =
        ScheduleEntry(program, hourStart + offsetMin * 60, durationMin * 60)

    @Test
    fun `an elapsed city pop segment remains after it leaves the rolling history`() {
        val timeline = ScheduleTimeline()
        timeline.update(
            fresh = listOf(
                entry(Program.CITYPOP, 0, 5),
                entry(Program.FUTUREFUNK, 5, 25),
                entry(Program.VAPORWAVE, 30, 4),
                entry(Program.FUTUREFUNK, 34, 26),
            ),
            now = hourStart + 40 * 60,
        )

        val afterHistoryRolls = timeline.update(
            fresh = listOf(
                entry(Program.FUTUREFUNK, 20, 10),
                entry(Program.VAPORWAVE, 30, 4),
                entry(Program.FUTUREFUNK, 34, 26),
            ),
            now = hourStart + 45 * 60,
        )

        assertTrue(afterHistoryRolls.any { it.program == Program.CITYPOP && it.startedAt == hourStart })
    }

    @Test
    fun `an elapsed vaporwave segment remains after it leaves the rolling history`() {
        val timeline = ScheduleTimeline()
        timeline.update(
            fresh = listOf(
                entry(Program.VAPORWAVE, 30, 4),
                entry(Program.FUTUREFUNK, 34, 26),
            ),
            now = hourStart + 40 * 60,
        )

        val afterHistoryRolls = timeline.update(
            fresh = listOf(entry(Program.FUTUREFUNK, 45, 15)),
            now = hourStart + 55 * 60,
        )

        assertTrue(afterHistoryRolls.any { it.program == Program.VAPORWAVE })
    }

    @Test
    fun `future queue estimates are replaced instead of retained`() {
        val timeline = ScheduleTimeline()
        timeline.update(
            fresh = listOf(entry(Program.VAPORWAVE, 30)),
            now = hourStart + 10 * 60,
        )

        val shiftedQueue = timeline.update(
            fresh = listOf(entry(Program.VAPORWAVE, 31)),
            now = hourStart + 11 * 60,
        )

        assertFalse(shiftedQueue.any { it.startedAt == hourStart + 30 * 60 })
        assertTrue(shiftedQueue.any { it.startedAt == hourStart + 31 * 60 })
    }

    @Test
    fun `a new hour discards the previous hour observations`() {
        val timeline = ScheduleTimeline()
        timeline.update(listOf(entry(Program.VAPORWAVE, 30)), now = hourStart + 40 * 60)

        val nextHour = timeline.update(
            fresh = listOf(
                ScheduleEntry(Program.CITYPOP, hourStart + 3600, 180),
            ),
            now = hourStart + 3600,
        )

        assertEquals(listOf(Program.CITYPOP), nextHour.map { it.program })
    }
}
