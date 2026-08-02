// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser

/**
 * The badge and ordering rules for the online-users list, kept out of the composable so the part
 * that is easy to get wrong can be tested.
 *
 * Ported from the website (`js/yumiChat-v2.js`): admins wear a red `@`, bridge bots a green `+`,
 * voiced (reserved) nicknames a blue `+`, and the list sorts admins first, then bots, then voice,
 * then everyone else — alphabetically within each rank.
 */
object UserRoster {

    /** The four kinds of entry, in the order they render and sort. */
    enum class Badge { ADMIN, BOT, VOICE, NONE }

    /**
     * A fallback for the blink between joining and the first user-list: the server is authoritative
     * via [ChatUser.role], but until it has spoken these three are shown as admins, exactly as the
     * site does.
     */
    val DEFAULT_ADMINS = setOf("shiro", "pixwell", "yumi")

    fun isAdmin(user: ChatUser): Boolean =
        user.role == "admin" || (user.role == null && user.nickname.lowercase() in DEFAULT_ADMINS)

    fun badge(user: ChatUser): Badge = when {
        isAdmin(user) -> Badge.ADMIN
        user.bot -> Badge.BOT
        user.role == "voice" -> Badge.VOICE
        else -> Badge.NONE
    }

    /** Admins first, then bots, then voice, then the rest; alphabetical within a rank. */
    fun sorted(users: List<ChatUser>): List<ChatUser> =
        users.sortedWith(compareBy({ badge(it).ordinal }, { it.nickname.lowercase() }))
}
