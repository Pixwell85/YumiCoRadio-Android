// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/** A signed-in account name is authoritative; guest input is consulted only without an account. */
fun effectiveChatNickname(accountUsername: String?, requestedNickname: String): String =
    accountUsername?.trim()?.takeIf(String::isNotEmpty) ?: requestedNickname
