// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/** Pure media decisions shared by the chat lists and their preview composables. */
object ChatMediaPolicy {

    fun isAnimatedGif(link: MediaLinks.Link): Boolean {
        val path = link.url.substringBefore('?').substringBefore('#')
        return link.kind == MediaLinks.Kind.IMAGE && path.endsWith(".gif", ignoreCase = true)
    }

    fun shouldAnimateGif(isGif: Boolean, isVisible: Boolean, isForeground: Boolean): Boolean =
        isGif && isVisible && isForeground
}
