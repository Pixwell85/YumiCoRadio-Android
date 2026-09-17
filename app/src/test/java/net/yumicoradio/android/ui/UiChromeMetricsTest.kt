// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiChromeMetricsTest {
    @Test
    fun `dropdown rows keep compact Win9x proportions`() {
        assertEquals(34, UiChromeMetrics.MenuRowHeightDp)
        assertEquals(20, UiChromeMetrics.MenuIconSizeDp)
        assertEquals(13, UiChromeMetrics.MenuTextSizeSp)
    }

    @Test
    fun `mini player transport glyph matches vote icon scale`() {
        assertEquals(18, UiChromeMetrics.MiniTransportGlyphSizeSp)
    }
}
