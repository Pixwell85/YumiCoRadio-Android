// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import org.junit.Test
import kotlin.test.assertEquals

class AndroidAutoLibraryPolicyTest {
    @Test
    fun `root offers the remembered live stream before quality settings`() {
        val children = androidAutoChildren(AndroidAutoLibrary.ROOT, StreamQuality.LOW)

        assertEquals(
            listOf(
                AndroidAutoEntry.Play(StreamQuality.LOW),
                AndroidAutoEntry.Browse(AndroidAutoLibrary.QUALITY_FOLDER),
            ),
            children,
        )
    }

    @Test
    fun `quality folder keeps every explicit stream choice`() {
        assertEquals(
            StreamQuality.entries.map(AndroidAutoEntry::Play),
            androidAutoChildren(AndroidAutoLibrary.QUALITY_FOLDER, StreamQuality.LOW),
        )
    }

    @Test
    fun `live shortcut resolves to the remembered stream`() {
        assertEquals(
            StreamQuality.AAC64,
            androidAutoQualityFor(AndroidAutoLibrary.LIVE, StreamQuality.AAC64),
        )
        assertEquals(
            StreamQuality.HIGH,
            androidAutoQualityFor(StreamQuality.HIGH.mediaId, StreamQuality.AAC64),
        )
    }
}
