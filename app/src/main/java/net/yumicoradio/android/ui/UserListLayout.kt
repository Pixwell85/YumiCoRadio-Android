// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

/** Keeps the optional roster from taking over the chat on a busy room. */
object UserListLayout {
    const val MAX_HEIGHT_FRACTION = 0.4f

    fun maxHeightDp(availableHeightDp: Float): Float = availableHeightDp * MAX_HEIGHT_FRACTION
}
