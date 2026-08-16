// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

/**
 * Suggestions for the composer as the user types, ported from the website's `setupAutocomplete`
 * (`js/yumiChat-v2.js`): `@` for nicknames, `:` for emote shortcuts, and a bare trailing token for
 * the symbolic emotes (`=)`, `^^`, `;)`).
 *
 * Pure, and scoped to the **end of the input** — the trigger is whatever is being typed at the
 * caret, which on a phone is the end of the field. Kept free of Android and Compose so the whole
 * rule is unit-tested.
 */
object ChatAutocomplete {
    const val MAX = 8
    private const val MAX_NICK = 15
    private val SPECIAL = charArrayOf('=', '^', ';', '<', '>')

    sealed interface Suggestion {
        data class Emote(val emote: Emotes.Emote) : Suggestion
        data class Mention(val nick: String) : Suggestion
    }

    /** [triggerStart] is the index of the trigger character (`@`/`:`) or the start of the word. */
    data class Result(val triggerStart: Int, val suggestions: List<Suggestion>)

    private val colonEmotes = (Emotes.PALETTE + Emotes.AUTOCOMPLETE_ALIASES)
        .filter { it.shortcut.startsWith(":") && it.shortcut.length >= 2 }
    private val symbolicEmotes = Emotes.PALETTE.filter { !it.shortcut.startsWith(":") }

    /** The suggestions for [text] typed at its end, or null when nothing should show. */
    fun suggest(text: String, users: List<String>, me: String?): Result? {
        val pos = text.length

        // 1. The nearest '@' or ':' before a space — the token being typed right now.
        var i = pos - 1
        while (i >= 0) {
            val ch = text[i]
            if (ch == ' ' || ch == '\n') break
            if (ch == '@') return mentionResult(text, i, pos, users, me)
            if (ch == ':') return colonResult(text, i, pos)
            i--
        }

        // 2. A nickname with spaces: re-scan across spaces to an '@', capped at '@' + max nick.
        i = pos - 1
        while (i >= 0 && pos - i <= MAX_NICK + 1) {
            val ch = text[i]
            if (ch == '\n' || ch == ':') break
            if (ch == '@') {
                mentionResult(text, i, pos, users, me)?.let { return it }
                break
            }
            i--
        }

        // 3. The trailing word on its own → a symbolic emote (`=)` needs one char, others two).
        var wordStart = pos
        var j = pos - 1
        while (j >= 0) {
            if (text[j] == ' ' || text[j] == '\n') break
            wordStart = j
            j--
        }
        val token = text.substring(wordStart, pos)
        val minLen = if (token.isNotEmpty() && token[0] in SPECIAL) 1 else 2
        if (token.isNotEmpty() && token.length >= minLen) {
            val matches = symbolicEmotes
                .filter { it.shortcut.startsWith(token, ignoreCase = true) }
                .take(MAX)
                .map { Suggestion.Emote(it) }
            if (matches.isNotEmpty()) return Result(wordStart, matches)
        }
        return null
    }

    /** Replace the trigger token (to the end of [text]) with the chosen item and a trailing space. */
    fun apply(text: String, triggerStart: Int, s: Suggestion): String {
        val insert = when (s) {
            is Suggestion.Emote -> s.emote.shortcut
            is Suggestion.Mention -> "@${s.nick}"
        }
        return text.substring(0, triggerStart) + insert + " "
    }

    private fun mentionResult(text: String, at: Int, pos: Int, users: List<String>, me: String?): Result? {
        val query = text.substring(at + 1, pos)
        if (query.isEmpty()) return null
        val matches = users
            .filter { it != me && it.startsWith(query, ignoreCase = true) }
            .take(MAX)
            .map { Suggestion.Mention(it) }
        return if (matches.isEmpty()) null else Result(at, matches)
    }

    private fun colonResult(text: String, colon: Int, pos: Int): Result? {
        val query = text.substring(colon + 1, pos)
        if (query.isEmpty()) return null
        val matches = colonEmotes
            .filter { inner(it.shortcut).startsWith(query, ignoreCase = true) }
            .take(MAX)
            .map { Suggestion.Emote(it) }
        return if (matches.isEmpty()) null else Result(colon, matches)
    }

    /** The shortcut without its leading ':' and an optional trailing ':' — `:smile:` → `smile`. */
    private fun inner(shortcut: String): String {
        val body = shortcut.removePrefix(":")
        return if (body.endsWith(":")) body.dropLast(1) else body
    }
}
