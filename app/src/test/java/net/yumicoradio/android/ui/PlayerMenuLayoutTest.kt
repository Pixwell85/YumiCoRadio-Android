// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import net.yumicoradio.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMenuLayoutTest {
    @Test
    fun `player menu groups every destination without hiding options`() {
        val entries = playerMenuLayout(includeBack = false)

        assertEquals(listOf("Radio Menu", "Community", "Options", "Help"), entries.map { it.label })
        assertEquals(
            listOf(MenuDestination.HISTORY, MenuDestination.RANKINGS, MenuDestination.SCHEDULE),
            (entries[0] as PlayerMenuEntry.Group).items.map { it.destination },
        )
        assertEquals(
            listOf(R.drawable.ic_win_history, R.drawable.ic_win_rankings, R.drawable.ic_win_schedule),
            (entries[0] as PlayerMenuEntry.Group).items.map { it.icon },
        )
        assertEquals(
            listOf(MenuDestination.CHAT, MenuDestination.ACCOUNT),
            (entries[1] as PlayerMenuEntry.Group).items.map { it.destination },
        )
        assertEquals(
            listOf(R.drawable.ic_win_chat, R.drawable.ic_win_account),
            (entries[1] as PlayerMenuEntry.Group).items.map { it.icon },
        )
        assertEquals(MenuDestination.OPTIONS, (entries[2] as PlayerMenuEntry.Action).destination)
        assertEquals(
            listOf(MenuDestination.CONTACT, MenuDestination.ABOUT),
            (entries[3] as PlayerMenuEntry.Group).items.map { it.destination },
        )
        assertEquals(
            listOf(R.drawable.ic_win_contact, R.drawable.ic_win_about),
            (entries[3] as PlayerMenuEntry.Group).items.map { it.icon },
        )
    }

    @Test
    fun `secondary screens keep a separate back action`() {
        val entries = playerMenuLayout(includeBack = true)

        assertEquals("◀", entries.first().label)
        assertEquals(MenuDestination.BACK, (entries.first() as PlayerMenuEntry.Action).destination)
    }
}
