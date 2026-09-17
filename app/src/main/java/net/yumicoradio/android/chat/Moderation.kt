// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatUser
import org.json.JSONObject

enum class ModerationAction(val label: String) {
    KICK("Kick"),
    KICK_DELETE("Kick and delete public messages"),
    MUTE_5M("Mute for 5 minutes"),
    MUTE_30M("Mute for 30 minutes"),
    MUTE_1H("Mute for 1 hour"),
    BAN_PERMANENT("Ban permanently"),
    BAN_PERMANENT_DELETE("Ban permanently and delete messages"),
    BAN_24H("Ban for 24 hours"),
    BAN_24H_DELETE("Ban for 24 hours and delete messages"),
    RESET_QUOTA("Reset upload quota"),
}

data class ModerationCommand(val event: String, val payload: JSONObject)

/** Mirrors the server's permission boundary so the UI never offers an action it cannot perform. */
object ModerationPolicy {
    fun canModerate(user: ChatUser?): Boolean =
        user != null && (UserRoster.isAdmin(user) || user.moderator)

    fun canToggleUploads(user: ChatUser?): Boolean = canModerate(user)

    fun actionsFor(actor: ChatUser?, target: ChatUser): List<ModerationAction> {
        if (!canModerate(actor) || actor == null) return emptyList()
        if (actor.nickname.equals(target.nickname, ignoreCase = true)) return emptyList()
        if (!UserRoster.isAdmin(actor) && UserRoster.isAdmin(target)) return emptyList()

        return buildList {
            add(ModerationAction.KICK)
            add(ModerationAction.KICK_DELETE)
            add(ModerationAction.MUTE_5M)
            add(ModerationAction.MUTE_30M)
            add(ModerationAction.MUTE_1H)
            if (UserRoster.isAdmin(actor)) {
                add(ModerationAction.BAN_PERMANENT)
                add(ModerationAction.BAN_PERMANENT_DELETE)
            }
            add(ModerationAction.BAN_24H)
            add(ModerationAction.BAN_24H_DELETE)
            add(ModerationAction.RESET_QUOTA)
        }
    }
}
