// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.packaging

import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class StoreIconTest {

    @Test
    fun `fastlane store icon is a valid 512 pixel PNG`() {
        val iconFile = File("../fastlane/metadata/android/en-US/images/icon.png")
        val icon = assertNotNull(
            ImageIO.read(iconFile),
            "${iconFile.path} must exist and be a readable PNG",
        )

        assertEquals(512, icon.width)
        assertEquals(512, icon.height)
    }
}
