// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * How big the chat text is drawn. A multiplier over the default sizes rather than absolute points,
 * so every piece of chat text — messages, the user list, the composer — scales together and keeps
 * its proportions.
 */
enum class ChatFontSize(val id: String, val label: String, val scale: Float) {
    SMALL("small", "Small", 0.85f),
    NORMAL("normal", "Normal", 1f),
    LARGE("large", "Large", 1.2f);

    companion object {
        val DEFAULT = NORMAL
        fun fromId(id: String?): ChatFontSize = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
