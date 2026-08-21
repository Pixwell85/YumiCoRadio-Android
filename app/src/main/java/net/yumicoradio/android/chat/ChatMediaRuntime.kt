// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/** The two Coil 2 decoder implementations supported by the app's Android API range. */
enum class GifDecoderKind { IMAGE_DECODER, GIF_DECODER }

/** API 28 introduced the faster platform ImageDecoder used for animated images. */
fun gifDecoderKind(apiLevel: Int): GifDecoderKind =
    if (apiLevel >= 28) GifDecoderKind.IMAGE_DECODER else GifDecoderKind.GIF_DECODER

/** Pure viewport rule shared by the public channel and private-message LazyColumns. */
object ChatMediaVisibility {
    fun isVisible(messageIndex: Int, visibleIndices: List<Int>): Boolean =
        visibleIndices.isEmpty() || messageIndex in visibleIndices
}
