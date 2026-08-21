// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMediaPolicyTest {

    @Test
    fun `gif query strings remain animated gif links`() {
        val link = MediaLinks.find(
            "https://s1.yumicoradio.net/chat/uploads/animation.GIF?v=1",
        ).single()

        assertTrue(ChatMediaPolicy.isAnimatedGif(link))
    }

    @Test
    fun `gif animation requires a visible foreground item`() {
        assertTrue(ChatMediaPolicy.shouldAnimateGif(isGif = true, isVisible = true, isForeground = true))
        assertFalse(ChatMediaPolicy.shouldAnimateGif(isGif = true, isVisible = false, isForeground = true))
        assertFalse(ChatMediaPolicy.shouldAnimateGif(isGif = true, isVisible = true, isForeground = false))
        assertFalse(ChatMediaPolicy.shouldAnimateGif(isGif = false, isVisible = true, isForeground = true))
    }
}
