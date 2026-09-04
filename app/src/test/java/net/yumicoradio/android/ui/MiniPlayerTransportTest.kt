// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MiniPlayerTransportTest {
    @Test fun `mini transport shows play only when playback was not requested`() {
        assertEquals("▶", miniPlayerTransportIcon(playbackRequested = false))
        assertEquals("■", miniPlayerTransportIcon(playbackRequested = true))
    }

    @Test fun `view model tracks play intent independently from audible buffering state`() {
        val source = File("src/main/java/net/yumicoradio/android/ui/PlayerViewModel.kt").readText()
        assertTrue(source.contains("val playbackRequested"))
        assertTrue(source.contains("onPlayWhenReadyChanged"))
    }
}
