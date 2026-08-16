// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * Process-memory ownership proof for reclaiming a nickname after Socket.IO changes socket id.
 *
 * The server binds the capability to one live nickname. Keeping that name beside the raw token
 * prevents a client-side identity change from accidentally presenting the previous user's proof.
 */
internal class ReconnectProof {
    private data class Value(val nickname: String, val token: String)

    @Volatile
    private var value: Value? = null

    fun accept(nickname: String, token: String) {
        if (nickname.isNotEmpty() && token.isNotEmpty()) {
            value = Value(nickname, token)
        }
    }

    fun forJoin(nickname: String): String? {
        val current = value ?: return null
        if (current.nickname.equals(nickname, ignoreCase = true)) return current.token
        clear()
        return null
    }

    fun clear() {
        value = null
    }
}
