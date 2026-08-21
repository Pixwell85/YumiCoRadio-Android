// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatVideoExternalHandoffStateTest {

    @Test
    fun `deferred radio intent is consumed only once`() {
        val state = ChatVideoExternalHandoffState()
        state.defer(shouldResumeRadio = true)

        assertTrue(state.consumeResumeIntent())
        assertFalse(state.consumeResumeIntent())
    }

    @Test
    fun `handoff that did not pause radio never resumes it`() {
        val state = ChatVideoExternalHandoffState()
        state.defer(shouldResumeRadio = false)

        assertFalse(state.consumeResumeIntent())
    }
}
