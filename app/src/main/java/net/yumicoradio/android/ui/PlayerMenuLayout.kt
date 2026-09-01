// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

internal enum class MenuDestination {
    BACK, HISTORY, RANKINGS, SCHEDULE, OPTIONS, CHAT, ACCOUNT, CONTACT, ABOUT,
}

internal sealed interface PlayerMenuEntry {
    val label: String

    data class Action(
        override val label: String,
        val destination: MenuDestination,
    ) : PlayerMenuEntry

    data class Group(
        override val label: String,
        val items: List<Action>,
    ) : PlayerMenuEntry
}

internal fun playerMenuLayout(includeBack: Boolean): List<PlayerMenuEntry> = buildList {
    if (includeBack) add(PlayerMenuEntry.Action("◀", MenuDestination.BACK))
    add(PlayerMenuEntry.Group("Radio Menu", listOf(
        PlayerMenuEntry.Action("History", MenuDestination.HISTORY),
        PlayerMenuEntry.Action("Rankings", MenuDestination.RANKINGS),
        PlayerMenuEntry.Action("Schedule", MenuDestination.SCHEDULE),
    )))
    add(PlayerMenuEntry.Group("Community", listOf(
        PlayerMenuEntry.Action("Chat", MenuDestination.CHAT),
        PlayerMenuEntry.Action("Account", MenuDestination.ACCOUNT),
    )))
    add(PlayerMenuEntry.Action("Options", MenuDestination.OPTIONS))
    add(PlayerMenuEntry.Group("Help", listOf(
        PlayerMenuEntry.Action("Contact", MenuDestination.CONTACT),
        PlayerMenuEntry.Action("About", MenuDestination.ABOUT),
    )))
}
