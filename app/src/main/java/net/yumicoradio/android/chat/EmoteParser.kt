package net.yumicoradio.android.chat

/**
 * Splits a message into text and emote runs.
 *
 * Pure, so the fiddly parts — longest-match-first, and leaving URLs alone — are tested without a
 * renderer in the way.
 */
object EmoteParser {

    sealed interface Token {
        data class Text(val value: String) : Token
        data class Emote(val emote: Emotes.Emote) : Token
    }

    private val URL = Regex("""\b(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Token> {
        if (text.isEmpty()) return emptyList()

        // URLs are full of colons and slashes that look like shortcuts. Chat is full of links, and
        // a mangled link is worse than a missing smiley, so their spans are copied through whole.
        val protectedSpans = URL.findAll(text).map { it.range }.toList()
        fun protectedAt(index: Int) = protectedSpans.any { index in it }

        val tokens = mutableListOf<Token>()
        val pending = StringBuilder()
        var i = 0

        while (i < text.length) {
            val match = if (protectedAt(i)) null else matchAt(text, i)
            if (match == null) {
                pending.append(text[i])
                i++
            } else {
                if (pending.isNotEmpty()) {
                    tokens += Token.Text(pending.toString())
                    pending.clear()
                }
                tokens += Token.Emote(match)
                i += match.shortcut.length
            }
        }
        if (pending.isNotEmpty()) tokens += Token.Text(pending.toString())
        return tokens
    }

    /** Longest first, so `:'D` is not shredded by a shorter shortcut sharing its prefix. */
    private fun matchAt(text: String, index: Int): Emotes.Emote? =
        Emotes.SHORTCUTS_LONGEST_FIRST
            .firstOrNull { text.startsWith(it, index) }
            ?.let { Emotes.BY_SHORTCUT[it] }
}
