// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * Nickname colours, derived exactly as the website derives them.
 *
 * The hash is deliberately the site's own (`getUserColor` in `js/yumiChat-v2.js`) rather than
 * anything nicer: the same person must appear in the same colour whether you are looking at the
 * app or at the browser.
 */
object NickColors {

    /** The site's sixteen IRC colours, in order — the index is the hash modulo this size. */
    val PALETTE = listOf(
        "#c33b3b", "#c73d13", "#9d9d00", "#3a993a",
        "#009999", "#3636b2", "#a328a3", "#7b7b7b",
        "#d54e26", "#e07e00", "#5fba3c", "#00b4b4",
        "#5a5adc", "#c040c0", "#4c4c4c", "#9b59b6",
    )

    /**
     * The same sixteen colours with the website's labels, in the same order as [PALETTE]. Used by
     * the picker for its swatches and their accessibility descriptions; "Auto" (no override) is not
     * in this list — it is the empty string and handled by the UI.
     */
    val NAMED: List<Pair<String, String>> = PALETTE.zip(
        listOf(
            "Red", "Orange", "Olive", "Green",
            "Teal", "Blue", "Purple", "Gray",
            "Brown", "Amber", "Lime", "Cyan",
            "Indigo", "Magenta", "Dark Gray", "Violet",
        ),
    )

    /**
     * [overrides] holds colours the server announced for a user (their own IRC colour pick), which
     * win over the derived one.
     */
    fun forNick(nick: String, overrides: Map<String, String> = emptyMap()): String {
        overrides[nick]?.takeIf { it.isNotBlank() }?.let { return it }

        // Matches JS: hash = charCode + ((hash << 5) - hash), which overflows to 32 bits there and
        // wraps identically in Kotlin's Int.
        var hash = 0
        for (ch in nick) {
            hash = ch.code + ((hash shl 5) - hash)
        }
        // Widened to Long before taking the absolute value: abs(Int.MIN_VALUE) is still negative and
        // would index outside the palette. JS avoids this because Math.abs promotes to a double.
        val index = (kotlin.math.abs(hash.toLong()) % PALETTE.size).toInt()
        return PALETTE[index]
    }
}
