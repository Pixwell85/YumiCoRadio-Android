// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser
import net.yumicoradio.android.chat.model.NickState

/**
 * The badge and ordering rules for the online-users list, kept out of the composable so the part
 * that is easy to get wrong can be tested.
 *
 * Ported from the website (`js/yumiChat-v2.js`): admins wear a red `@`, moderators a blue `@`,
 * bridge bots a green `+`, voiced nicknames a blue `+`, and the list follows that rank order.
 */
object UserRoster {

    private val RESERVED_ROLES = setOf("admin", "voice", "user")

    /** The four kinds of entry, in the order they render and sort. */
    enum class Badge { ADMIN, MODERATOR, BOT, VOICE, NONE }

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
        user.moderator -> Badge.MODERATOR
        user.bot -> Badge.BOT
        user.role in RESERVED_ROLES -> Badge.VOICE
        else -> Badge.NONE
    }

    /**
     * Whether Chat Options may offer reserved-nickname password storage for the active user.
     *
     * Account-backed users have role `user`; legacy reservations use `voice`, and owners use
     * `admin`. Unknown wire values must not be treated as reserved.
     * NickState is part of the decision so a saved nickname, a stale roster row, or an in-progress
     * nickname change cannot expose password controls before the server confirms the joined user.
     */
    fun isCurrentNicknameReserved(nick: NickState, users: List<ChatUser>): Boolean {
        val joined = (nick as? NickState.Joined)?.nickname ?: return false
        return users.any { user ->
            user.nickname.equals(joined, ignoreCase = true) &&
                user.role?.lowercase() in RESERVED_ROLES
        }
    }

    /** Admins, moderators, bots, voice, then the rest; alphabetical within a rank. */
    fun sorted(users: List<ChatUser>): List<ChatUser> =
        users.sortedWith(compareBy({ badge(it).ordinal }, { it.nickname.lowercase() }))
}
