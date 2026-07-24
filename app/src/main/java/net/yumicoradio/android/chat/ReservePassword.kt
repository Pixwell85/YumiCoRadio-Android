// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * Validates a password the user is choosing for a reserved nickname, before it leaves the device.
 *
 * The server checks length again as a fallback (`reserve-error {length}`), but the two-field match
 * is caught only here: the confirmation never travels. Length is checked first, so a short pair
 * that also differs reads as "too short" — the more actionable message.
 */
object ReservePassword {

    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128

    enum class Error { TOO_SHORT, TOO_LONG, MISMATCH }

    /** The problem with this pair, or null when it is fit to send. */
    fun validate(password: String, confirm: String): Error? = when {
        password.length < MIN_LENGTH -> Error.TOO_SHORT
        password.length > MAX_LENGTH -> Error.TOO_LONG
        password != confirm -> Error.MISMATCH
        else -> null
    }
}
