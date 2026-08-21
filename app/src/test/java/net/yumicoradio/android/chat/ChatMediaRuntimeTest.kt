// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMediaRuntimeTest {

    @Test
    fun `API 28 and newer use the platform animated image decoder`() {
        assertEquals(GifDecoderKind.IMAGE_DECODER, gifDecoderKind(28))
        assertEquals(GifDecoderKind.IMAGE_DECODER, gifDecoderKind(36))
    }

    @Test
    fun `older supported Android versions use the compatibility GIF decoder`() {
        assertEquals(GifDecoderKind.GIF_DECODER, gifDecoderKind(24))
        assertEquals(GifDecoderKind.GIF_DECODER, gifDecoderKind(27))
    }

    @Test
    fun `empty first layout treats its composed message as visible`() {
        assertTrue(ChatMediaVisibility.isVisible(messageIndex = 4, visibleIndices = emptyList()))
    }

    @Test
    fun `populated layout follows the actual visible message indices`() {
        assertTrue(ChatMediaVisibility.isVisible(messageIndex = 4, visibleIndices = listOf(3, 4, 5)))
        assertFalse(ChatMediaVisibility.isVisible(messageIndex = 8, visibleIndices = listOf(3, 4, 5)))
    }
}
