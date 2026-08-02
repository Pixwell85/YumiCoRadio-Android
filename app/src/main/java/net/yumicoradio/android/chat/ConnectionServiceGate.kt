// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.NickState

/**
 * Whether the user is in — or on their way into — a chat session worth keeping a socket alive for.
 *
 * The predicate is deliberately the nickname state, not [ConnectionState]. socket.io reports a
 * transient network drop as `DISCONNECTED`, the same value a deliberate leave produces, so the
 * connection flow cannot tell "the user left" from "the wire blipped". The nick flow can: a leave
 * lands on [NickState.Idle], while a blip leaves the session state (Joined, or a handshake in
 * progress) untouched for the auto-reconnect to replay. Gating on nick keeps the service up across a
 * reconnect and drops it only when the user actually walks away.
 */
val NickState.hasSession: Boolean
    get() = when (this) {
        is NickState.Joining,
        is NickState.NeedsPassword,
        is NickState.SettingPassword,
        is NickState.Joined -> true
        NickState.Idle,
        NickState.NeedsNick,
        is NickState.Rejected -> false
    }

/**
 * Whether the background [ChatConnectionService] should be running.
 *
 * Two independent reasons: the user opted to stay connected in the background *and* is in a session,
 * or a file transfer is holding the process alive across a pick (which happens regardless of the
 * preference). Kept pure so the whole truth table can be unit-tested without Android.
 */
fun shouldRunConnectionService(
    stayConnected: Boolean,
    nick: NickState,
    transferHold: Boolean,
): Boolean = transferHold || (stayConnected && nick.hasSession)
