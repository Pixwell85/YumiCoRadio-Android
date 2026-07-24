// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatMessage

/** What the chat is allowed to interrupt you for. */
enum class NotificationMode(val id: String, val label: String) {
    ALL("all", "Every message"),
    MENTIONS("mentions", "Mentions and private messages"),
    NONE("none", "Nothing");

    companion object {
        val DEFAULT = MENTIONS
        fun fromId(id: String?): NotificationMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Decides whether a message deserves a notification.
 *
 * Pure, because this is the rule most likely to be wrong in a way you only notice when your phone
 * buzzes at 3am — or stays silent when someone needed you.
 */
object NotificationPolicy {

    fun shouldNotify(
        message: ChatMessage,
        mode: NotificationMode,
        myNickname: String,
        isPm: Boolean,
    ): Boolean {
        if (mode == NotificationMode.NONE) return false
        // Join/quit notices and the MOTD are the server talking; in ALL mode every arrival would
        // otherwise buzz the phone.
        if (message.isSystem || message.isMotd) return false
        if (message.user.equals(myNickname, ignoreCase = true)) return false

        return when (mode) {
            NotificationMode.ALL -> true
            NotificationMode.MENTIONS -> isPm || mentions(message.text, myNickname)
            NotificationMode.NONE -> false
        }
    }

    /**
     * A whole-word match. Without the boundaries "Shiro" would fire on "Shirokuma", making the
     * mentions mode as noisy as the all mode for anyone with a short nickname.
     */
    private fun mentions(text: String, nickname: String): Boolean {
        if (nickname.isBlank()) return false
        val pattern = Regex(
            "(?<![\\p{L}\\p{N}_])${Regex.escape(nickname)}(?![\\p{L}\\p{N}_])",
            RegexOption.IGNORE_CASE,
        )
        return pattern.containsMatchIn(text)
    }
}
