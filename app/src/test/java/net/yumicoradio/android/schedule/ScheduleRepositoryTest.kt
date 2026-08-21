// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.schedule

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.yumicoradio.android.metadata.AzuraSnapshot
import net.yumicoradio.android.metadata.model.NowPlaying
import net.yumicoradio.android.metadata.model.RecentTrack
import org.junit.Test
import kotlin.test.assertEquals

class ScheduleRepositoryTest {

    @Test
    fun `schedule fetches its own live snapshot without depending on audio playback`() = runBlocking {
        val hourStart = 1_784_390_400L
        var snapshotFetches = 0
        val snapshot = AzuraSnapshot(
            nowPlaying = NowPlaying(
                artist = "Current",
                title = "Track",
                artworkUrl = null,
                listeners = 0,
                online = true,
                playedAt = hourStart + 20 * 60,
                duration = 600,
                playlist = "Yumi",
            ),
            recent = listOf(
                RecentTrack(
                    artist = "Past",
                    title = "City Pop",
                    imageUrl = null,
                    uts = hourStart,
                    playlist = "CityPop",
                    duration = 300,
                ),
            ),
        )
        val queue = listOf(ScheduleEntry(Program.VAPORWAVE, hourStart + 30 * 60, 180))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repository = ScheduleRepository(
            fetchQueue = { queue },
            fetchSnapshot = { snapshotFetches++; snapshot },
            scope = scope,
            io = Dispatchers.Unconfined,
            clock = { hourStart + 25 * 60 },
        )

        try {
            repository.start()
            delay(1)

            assertEquals(1, snapshotFetches)
            assertEquals(
                listOf(Program.CITYPOP, Program.FUTUREFUNK, Program.VAPORWAVE),
                repository.timeline.value.map { it.program },
            )
        } finally {
            repository.stop()
            scope.cancel()
        }
    }
}
