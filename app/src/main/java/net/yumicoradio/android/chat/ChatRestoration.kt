// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ConnectionState
import net.yumicoradio.android.chat.model.NickState

/** What the Chat screen should do when it first receives its persisted nickname state. */
sealed interface ChatEntryAction {
    data object WAIT : ChatEntryAction
    data object ASK_NICKNAME : ChatEntryAction
    data object NONE : ChatEntryAction
    data class JoinNickname(val nickname: String) : ChatEntryAction
}

/**
 * Decides whether entering Chat should wait, reconnect a saved nickname, or ask for one.
 *
 * A nullable saved nickname is deliberate: `null` means DataStore has not emitted yet, while an
 * empty string means it has emitted and no nickname exists. Treating both as empty caused the
 * blank nickname dialog after Android recreated the process.
 */
fun chatEntryAction(
    connection: ConnectionState,
    nick: NickState,
    savedNick: String?,
): ChatEntryAction {
    if (connection != ConnectionState.DISCONNECTED || nick != NickState.Idle) {
        return ChatEntryAction.NONE
    }
    if (savedNick == null) return ChatEntryAction.WAIT
    val normalized = savedNick.trim()
    return if (normalized.isEmpty()) {
        ChatEntryAction.ASK_NICKNAME
    } else {
        ChatEntryAction.JoinNickname(normalized)
    }
}

/** Returns the nickname to restore at process startup, or null when recovery is not warranted. */
fun chatRestoreNickname(
    stayConnected: Boolean,
    sessionWanted: Boolean,
    savedNick: String,
    connection: ConnectionState,
    nick: NickState,
): String? {
    if (!stayConnected || !sessionWanted) return null
    return (chatEntryAction(connection, nick, savedNick) as? ChatEntryAction.JoinNickname)?.nickname
}

/**
 * Rehydrates the process-local repository from the persisted session intent.
 *
 * Android dependencies are supplied as functions so ordering is explicit and unit-testable: an
 * encrypted reserved-nickname password must be primed before Socket.IO emits its first join.
 */
class ChatSessionRestorer(
    private val loadPassword: suspend (String) -> String?,
    private val primePassword: (String) -> Unit,
    private val connect: (String) -> Unit,
) {
    suspend fun restore(
        stayConnected: Boolean,
        sessionWanted: Boolean,
        savedNick: String,
        connection: ConnectionState,
        nick: NickState,
    ): Boolean {
        val nickname = chatRestoreNickname(
            stayConnected = stayConnected,
            sessionWanted = sessionWanted,
            savedNick = savedNick,
            connection = connection,
            nick = nick,
        ) ?: return false

        loadPassword(nickname)?.let(primePassword)
        connect(nickname)
        return true
    }
}
