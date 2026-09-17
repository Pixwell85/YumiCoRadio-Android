// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.annotation.DrawableRes
import net.yumicoradio.android.R

internal enum class MenuDestination {
    BACK, HISTORY, RANKINGS, SCHEDULE, OPTIONS, CHAT, ACCOUNT, CONTACT, ABOUT,
}

internal sealed interface PlayerMenuEntry {
    val label: String

    data class Action(
        override val label: String,
        val destination: MenuDestination,
        @param:DrawableRes val icon: Int? = null,
    ) : PlayerMenuEntry

    data class Group(
        override val label: String,
        val items: List<Action>,
    ) : PlayerMenuEntry
}

internal fun playerMenuLayout(includeBack: Boolean): List<PlayerMenuEntry> = buildList {
    if (includeBack) add(PlayerMenuEntry.Action("◀", MenuDestination.BACK))
    add(PlayerMenuEntry.Group("Radio Menu", listOf(
        PlayerMenuEntry.Action("History", MenuDestination.HISTORY, R.drawable.ic_win_history),
        PlayerMenuEntry.Action("Rankings", MenuDestination.RANKINGS, R.drawable.ic_win_rankings),
        PlayerMenuEntry.Action("Schedule", MenuDestination.SCHEDULE, R.drawable.ic_win_schedule),
    )))
    add(PlayerMenuEntry.Group("Community", listOf(
        PlayerMenuEntry.Action("Chat", MenuDestination.CHAT, R.drawable.ic_win_chat),
        PlayerMenuEntry.Action("Account", MenuDestination.ACCOUNT, R.drawable.ic_win_account),
    )))
    add(PlayerMenuEntry.Action("Options", MenuDestination.OPTIONS, R.drawable.ic_win_settings))
    add(PlayerMenuEntry.Group("Help", listOf(
        PlayerMenuEntry.Action("Contact", MenuDestination.CONTACT, R.drawable.ic_win_contact),
        PlayerMenuEntry.Action("About", MenuDestination.ABOUT, R.drawable.ic_win_about),
    )))
}
