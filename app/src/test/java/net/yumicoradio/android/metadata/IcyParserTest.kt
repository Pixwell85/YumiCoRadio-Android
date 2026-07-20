package net.yumicoradio.android.metadata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IcyParserTest {
    @Test fun splits_artist_and_title_on_first_dash() {
        val r = IcyParser.parse("Tatsuro Yamashita - Ride On Time")!!
        assertEquals("Tatsuro Yamashita", r.artist)
        assertEquals("Ride On Time", r.title)
    }
    @Test fun keeps_dashes_inside_title() {
        val r = IcyParser.parse("Anri - Last Summer - Whisper")!!
        assertEquals("Anri", r.artist)
        assertEquals("Last Summer - Whisper", r.title)
    }
    @Test fun no_dash_all_goes_to_title_empty_artist() {
        val r = IcyParser.parse("Station Jingle")!!
        assertEquals("", r.artist)
        assertEquals("Station Jingle", r.title)
    }
    @Test fun trims_whitespace() {
        val r = IcyParser.parse("  Mariya Takeuchi  -  Plastic Love  ")!!
        assertEquals("Mariya Takeuchi", r.artist)
        assertEquals("Plastic Love", r.title)
    }
    @Test fun blank_or_null_returns_null() {
        assertNull(IcyParser.parse(""))
        assertNull(IcyParser.parse("   "))
        assertNull(IcyParser.parse(null))
    }
}
