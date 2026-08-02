// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AudioTagsTest {

    @Test
    fun `reads artist title and album`() {
        val t = AudioTags.parse("""{"artist":"Yumi","title":"Song","album":"Rec"}""")!!
        assertEquals("Yumi", t.artist)
        assertEquals("Song", t.title)
        assertEquals("Rec", t.album)
    }

    @Test
    fun `missing fields are null`() {
        val t = AudioTags.parse("""{"title":"Song"}""")!!
        assertNull(t.artist)
        assertEquals("Song", t.title)
        assertNull(t.album)
    }

    @Test
    fun `blank fields are treated as absent`() {
        val t = AudioTags.parse("""{"artist":"  ","title":"Song","album":""}""")!!
        assertNull(t.artist)
        assertNull(t.album)
        assertEquals("Song", t.title)
    }

    @Test
    fun `an all-blank object yields null`() {
        assertNull(AudioTags.parse("""{"artist":"","title":"","album":""}"""))
    }

    @Test
    fun `malformed json yields null`() {
        assertNull(AudioTags.parse("not json"))
    }
}
