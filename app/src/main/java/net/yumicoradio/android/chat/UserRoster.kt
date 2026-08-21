// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser
import net.yumicoradio.android.chat.model.NickState

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

    /**
     * Whether Chat Options may offer reserved-nickname password storage for the active user.
     *
     * The server contract has exactly two reserved roles: `admin` and `voice`. Checking only for a
     * non-null role made any unexpected wire value (for example `"user"` or `"null"`) look reserved.
     * NickState is part of the decision so a saved nickname, a stale roster row, or an in-progress
     * nickname change cannot expose password controls before the server confirms the joined user.
     */
    fun isCurrentNicknameReserved(nick: NickState, users: List<ChatUser>): Boolean {
        val joined = (nick as? NickState.Joined)?.nickname ?: return false
        return users.any { user ->
            user.nickname.equals(joined, ignoreCase = true) &&
                (user.role.equals("admin", ignoreCase = true) ||
                    user.role.equals("voice", ignoreCase = true))
        }
    }

    /** Admins first, then bots, then voice, then the rest; alphabetical within a rank. */
    fun sorted(users: List<ChatUser>): List<ChatUser> =
        users.sortedWith(compareBy({ badge(it).ordinal }, { it.nickname.lowercase() }))
}
