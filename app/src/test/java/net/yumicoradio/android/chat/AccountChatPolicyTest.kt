// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountChatPolicyTest {
    @Test fun `signed-in nickname overrides guest input`() {
        assertEquals("Shiro", effectiveChatNickname(" Shiro ", "Guest"))
    }

    @Test fun `guest nickname remains unchanged without account`() {
        assertEquals("Guest", effectiveChatNickname(null, "Guest"))
        assertEquals("Guest", effectiveChatNickname("", "Guest"))
    }
}
