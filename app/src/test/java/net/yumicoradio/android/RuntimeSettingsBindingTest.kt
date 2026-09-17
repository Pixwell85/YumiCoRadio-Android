// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeSettingsBindingTest {
    @Test
    fun `persisted runtime settings are restored without opening their screens`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val enabled = MutableStateFlow(true)
        val gains = MutableStateFlow(listOf(-2, -1, 0, 1, 2, 3, 4, 5, 6, 7))
        val nickColor = MutableStateFlow("#ff8000")
        var appliedEnabled: Boolean? = null
        var appliedGains: List<Int>? = null
        var appliedColor: String? = null

        bindRuntimeSettings(
            scope = scope,
            eqEnabled = enabled,
            eqGains = gains,
            chatNickColor = nickColor,
            applyEqEnabled = { appliedEnabled = it },
            applyEqGains = { appliedGains = it },
            applyChatNickColor = { appliedColor = it },
        )
        yield()

        assertEquals(true, appliedEnabled)
        assertEquals(gains.value, appliedGains)
        assertEquals("#ff8000", appliedColor)

        enabled.value = false
        gains.value = List(10) { 4 }
        nickColor.value = "#0080ff"
        yield()

        assertEquals(false, appliedEnabled)
        assertEquals(List(10) { 4 }, appliedGains)
        assertEquals("#0080ff", appliedColor)
        scope.cancel()
    }
}
