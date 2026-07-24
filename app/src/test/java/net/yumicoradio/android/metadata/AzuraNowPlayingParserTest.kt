// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Trimmed to the fields we consume; shape copied from a live /api/nowplaying/1 response. */
private val LIVE = """
{
  "listeners": {"total": 64, "unique": 64, "current": 64},
  "now_playing": {
    "played_at": 1784269894,
    "duration": 230,
    "song": {
      "art": "https://yumicoradio.net/api/station/yumi_co._radio/art/51d84aac78d2d0fc4eb43dd6-1774807290.jpg",
      "text": "Campioni d'Italia - 東京の夜",
      "artist": "Campioni d'Italia",
      "title": "東京の夜",
      "album": "東京高速道路 EP"
    }
  },
  "song_history": [
    {
      "played_at": 1784269700,
      "song": {
        "art": "https://yumicoradio.net/api/station/yumi_co._radio/art/f024d3ccc0e87a4f66a820c5-1781290703.jpg",
        "artist": "Amherst",
        "title": "Collision Course (Feat. Rollergirl)"
      }
    },
    {
      "played_at": 1784269500,
      "song": {
        "art": "https://yumicoradio.net/api/station/yumi_co._radio/art/814f4c3914342db4e6c39017",
        "artist": "Kizuna AI",
        "title": "Again (Moe Shop Remix)"
      }
    }
  ]
}
""".trimIndent()

class AzuraNowPlayingParserTest {

    @Test
    fun `reads now playing track with embedded artwork`() {
        val np = AzuraNowPlayingParser.parse(LIVE)!!.nowPlaying
        assertEquals("Campioni d'Italia", np.artist)
        assertEquals("東京の夜", np.title)
        assertEquals(64, np.listeners)
        assertTrue(np.online)
        assertEquals(
            "https://yumicoradio.net/api/station/yumi_co._radio/art/51d84aac78d2d0fc4eb43dd6-1774807290.jpg",
            np.artworkUrl,
        )
    }

    @Test
    fun `history keeps played_at and per-track artwork`() {
        val recent = AzuraNowPlayingParser.parse(LIVE)!!.recent
        assertEquals(2, recent.size)
        assertEquals("Amherst", recent[0].artist)
        assertEquals(1784269700L, recent[0].uts)
        assertEquals(
            "https://yumicoradio.net/api/station/yumi_co._radio/art/f024d3ccc0e87a4f66a820c5-1781290703.jpg",
            recent[0].imageUrl,
        )
    }

    @Test
    fun `station default artwork is passed through unchanged`() {
        // AzuraCast serves the station's default image (no -timestamp suffix) for tracks with no
        // embedded art. We show it as-is rather than guessing a cover from a text search.
        val recent = AzuraNowPlayingParser.parse(LIVE)!!.recent
        assertEquals(
            "https://yumicoradio.net/api/station/yumi_co._radio/art/814f4c3914342db4e6c39017",
            recent[1].imageUrl,
        )
    }

    @Test
    fun `reads played_at and duration for the timer`() {
        val np = AzuraNowPlayingParser.parse(LIVE)!!.nowPlaying
        assertEquals(1784269894L, np.playedAt)
        assertEquals(230, np.duration)
    }

    @Test
    fun `missing timing fields default to zero`() {
        val raw = """
        {"listeners":{"current":1},
         "now_playing":{"song":{"art":"x","artist":"A","title":"T"}}}
        """.trimIndent()
        val np = AzuraNowPlayingParser.parse(raw)!!.nowPlaying
        assertEquals(0L, np.playedAt)
        assertEquals(0, np.duration)
    }

    @Test
    fun `missing now_playing yields null`() {
        assertNull(AzuraNowPlayingParser.parse("""{"listeners":{"current":3}}"""))
    }

    @Test
    fun `malformed json yields null`() {
        assertNull(AzuraNowPlayingParser.parse("not json"))
    }

    @Test
    fun `a malformed history entry is skipped, not the whole snapshot`() {
        // A single history row missing its `song` object must not discard the valid now_playing.
        val raw = """
        {"listeners":{"current":1},
         "now_playing":{"played_at":1,"song":{"artist":"A","title":"T"}},
         "song_history":[
           {"played_at":2},
           {"played_at":3,"song":{"artist":"B","title":"U"}}
         ]}
        """.trimIndent()
        val snap = AzuraNowPlayingParser.parse(raw)!!
        assertEquals("A", snap.nowPlaying.artist)
        assertEquals(1, snap.recent.size)
        assertEquals("B", snap.recent[0].artist)
    }

    @Test
    fun `blank artwork becomes null`() {
        val raw = """
        {"listeners":{"current":1},
         "now_playing":{"played_at":1,"song":{"art":"","artist":"A","title":"T"}}}
        """.trimIndent()
        assertNull(AzuraNowPlayingParser.parse(raw)!!.nowPlaying.artworkUrl)
    }
}
