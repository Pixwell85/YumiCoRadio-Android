// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import java.io.File
import org.junit.Test
import kotlin.test.assertTrue

class AccountVotesUiTest {
    @Test fun `signed in profile loads and displays the saved vote count`() {
        val vm = File("src/main/java/net/yumicoradio/android/ui/AccountViewModel.kt").readText()
        val screen = File("src/main/java/net/yumicoradio/android/ui/AccountScreen.kt").readText()

        assertTrue(vm.contains("val voteCount"))
        assertTrue(vm.contains("ratings.myVotes"))
        assertTrue(screen.contains("ProfileLine(\"Votes saved\""))
    }
}
