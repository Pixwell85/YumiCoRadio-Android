// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.update

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FdroidPackageParserTest {
    @Test
    fun `reads the suggested version code from the F-Droid package response`() {
        val json = """{"suggestedVersionCode":116,"packages":[{"versionName":"0.38.2008"}]}"""
        assertEquals(116, FdroidPackageParser.suggestedVersionCode(json))
    }

    @Test
    fun `rejects missing malformed or non-positive version codes`() {
        assertNull(FdroidPackageParser.suggestedVersionCode("{}"))
        assertNull(FdroidPackageParser.suggestedVersionCode("not json"))
        assertNull(FdroidPackageParser.suggestedVersionCode("""{"suggestedVersionCode":0}"""))
    }
}
