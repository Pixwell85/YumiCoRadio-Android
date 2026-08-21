// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import org.junit.Test
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertFalse

class HistoryActionLayoutTest {

    @Test
    fun `history actions use an evenly spaced centred grid`() {
        val source = File("src/main/java/net/yumicoradio/android/ui/HistoryScreen.kt").readText()

        assertFalse(source.contains("Arrangement.SpaceBetween"))
        assertContains(source, "horizontalArrangement = Arrangement.spacedBy(8.dp)")
        assertContains(source, "horizontalAlignment = Alignment.CenterHorizontally")
        assertContains(source, "modifier = Modifier.width(92.dp)")
    }
}
