// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * Splits a message into plain runs and `@mentions`, so the renderer can bold and colour the latter
 * exactly as the website does.
 *
 * Ported from `js/yumiChat-v2.js` (`resolveMention`): nicknames may hold spaces, dots, accents and
 * CJK, so a bare `\w` token will not do — each `@` is matched against the known user list, longest
 * name first, and only when the character that follows is not itself a letter or digit (so `@Bob`
 * does not light up inside `@Bobby`). An `@` that resolves to nobody stays literal text.
 */
object MentionParser {

    sealed interface Token {
        data class Text(val value: String) : Token

        /** A resolved mention. [nick] is the canonical name from the user list, not what was typed. */
        data class Mention(val nick: String, val isSelf: Boolean) : Token
    }

    private val LETTER_OR_DIGIT = Regex("[\\p{L}\\p{N}]")

    fun parse(text: String, users: List<String>, me: String?): List<Token> {
        if (text.isEmpty() || users.isEmpty()) return listOf(Token.Text(text))

        val tokens = mutableListOf<Token>()
        val pending = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (text[i] == '@') {
                val matched = resolve(text, i, users)
                if (matched != null) {
                    if (pending.isNotEmpty()) {
                        tokens += Token.Text(pending.toString()); pending.clear()
                    }
                    tokens += Token.Mention(matched, isSelf = me != null && matched.equals(me, ignoreCase = true))
                    i += 1 + matched.length
                    continue
                }
            }
            pending.append(text[i])
            i++
        }
        if (pending.isNotEmpty()) tokens += Token.Text(pending.toString())
        return tokens
    }

    /** The longest known nick that the text spells out just after the `@` at [atIndex], or null. */
    private fun resolve(text: String, atIndex: Int, users: List<String>): String? {
        val rest = text.substring(atIndex + 1)
        var best: String? = null
        for (u in users) {
            if (u.isEmpty() || rest.length < u.length) continue
            if (!rest.regionMatches(0, u, 0, u.length, ignoreCase = true)) continue
            val after = rest.getOrNull(u.length)
            if (after != null && LETTER_OR_DIGIT.matches(after.toString())) continue
            if (best == null || u.length > best!!.length) best = u
        }
        return best
    }
}
