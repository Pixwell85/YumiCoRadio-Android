// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import java.io.File
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserListLayoutTest {

    @Test
    fun `user panel is capped at forty percent of the available chat height`() {
        assertEquals(240f, UserListLayout.maxHeightDp(availableHeightDp = 600f))
        assertEquals(320f, UserListLayout.maxHeightDp(availableHeightDp = 800f))
    }

    @Test
    fun `chat panel uses its available height instead of the physical screen height`() {
        val source = sequenceOf(
            File("src/main/java/net/yumicoradio/android/ui/ChatScreen.kt"),
            File("app/src/main/java/net/yumicoradio/android/ui/ChatScreen.kt"),
        ).first { it.isFile }.readText()

        assertFalse(source.contains("LocalConfiguration.current.screenHeightDp"))
        assertTrue(source.contains("BoxWithConstraints"))
        assertTrue(source.contains("UserListLayout.maxHeightDp(maxHeight.value)"))
    }
}
