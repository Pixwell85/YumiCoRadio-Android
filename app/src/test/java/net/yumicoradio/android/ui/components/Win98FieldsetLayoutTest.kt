// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Win98FieldsetLayoutTest {
    @Test fun `legend uses a tight line box shifted above the etched border`() {
        assertEquals(11f, Win98FieldsetLegendLayout.fontSizeSp)
        assertEquals(11f, Win98FieldsetLegendLayout.lineHeightSp)

        val borderInsideLegend = -Win98FieldsetLegendLayout.offsetYDp
        assertTrue(borderInsideLegend > Win98FieldsetLegendLayout.lineHeightSp / 2f)
    }
}
