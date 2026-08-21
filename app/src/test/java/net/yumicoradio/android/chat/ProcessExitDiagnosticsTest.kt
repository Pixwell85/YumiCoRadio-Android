// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals

class ProcessExitDiagnosticsTest {

    @Test fun `maps crash and responsiveness exits`() {
        assertEquals("App crash", processExitSummary(4))
        assertEquals("Native app crash", processExitSummary(5))
        assertEquals("App not responding (ANR)", processExitSummary(6))
        assertEquals("App startup failed", processExitSummary(7))
    }

    @Test fun `maps resource and Android lifecycle exits`() {
        assertEquals("Android reclaimed memory", processExitSummary(3))
        assertEquals("Excessive resource use", processExitSummary(9))
        assertEquals("Android froze the process", processExitSummary(14))
        assertEquals("Required system process stopped", processExitSummary(12))
    }

    @Test fun `maps deliberate and package exits`() {
        assertEquals("App exited normally", processExitSummary(1))
        assertEquals("Process was terminated", processExitSummary(2))
        assertEquals("Android or the user requested a stop", processExitSummary(10))
        assertEquals("App was force-stopped", processExitSummary(11))
        assertEquals("Permission changed", processExitSummary(8))
        assertEquals("App state changed", processExitSummary(15))
        assertEquals("App was updated", processExitSummary(16))
    }

    @Test fun `unknown platform values stay honest`() {
        assertEquals("Unknown reason", processExitSummary(0))
        assertEquals("Unknown reason", processExitSummary(13))
        assertEquals("Unknown reason", processExitSummary(999))
    }
}
