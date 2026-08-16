// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmoteParserTest {

    private fun textOf(tokens: List<EmoteParser.Token>) =
        tokens.filterIsInstance<EmoteParser.Token.Text>().joinToString("") { it.value }

    private fun shortcutsOf(tokens: List<EmoteParser.Token>) =
        tokens.filterIsInstance<EmoteParser.Token.Emote>().map { it.emote.shortcut }

    @Test
    fun `plain text yields a single text token`() {
        val tokens = EmoteParser.parse("hello there")
        assertEquals(listOf(EmoteParser.Token.Text("hello there")), tokens)
    }

    @Test
    fun `a shortcut becomes an emote token`() {
        val tokens = EmoteParser.parse("hi :D")
        assertEquals(listOf(":D"), shortcutsOf(tokens))
        assertEquals("hi ", textOf(tokens))
    }

    /**
     * The longest match must win. `:'D` and `:'(` share a prefix with `:'`, and `:D` is a prefix of
     * nothing but sits inside `:'D` — matching short-first would shred these.
     */
    @Test
    fun `the longest shortcut wins`() {
        assertEquals(listOf(":'D"), shortcutsOf(EmoteParser.parse(":'D")))
        assertEquals(listOf(":'("), shortcutsOf(EmoteParser.parse(":'(")))
    }

    @Test
    fun `several emotes in one line are all found`() {
        val tokens = EmoteParser.parse(":D hey :( there xD")
        assertEquals(listOf(":D", ":(", "xD"), shortcutsOf(tokens))
        assertEquals(" hey  there ", textOf(tokens))
    }

    @Test
    fun `adjacent emotes do not need separating text`() {
        assertEquals(listOf(":D", ":D"), shortcutsOf(EmoteParser.parse(":D:D")))
    }

    @Test
    fun `aliases resolve to the same picture as the canonical shortcut`() {
        val canonical = Emotes.BY_SHORTCUT[":)"]!!
        val alias = EmoteParser.parse(":-)").filterIsInstance<EmoteParser.Token.Emote>().single()
        assertEquals(canonical.file, alias.emote.file)
    }

    @Test
    fun `new website emotes and aliases use the matching pictures`() {
        val expectedPalette = mapOf(
            ":wip:" to "Emojis_32x32_842.png",
            ":party:" to "Emojis_32x32_577.png",
            ":announce:" to "Emojis_32x32_268.png",
        )
        val palette = Emotes.PALETTE.associateBy { it.shortcut }
        expectedPalette.forEach { (shortcut, file) ->
            assertEquals(file, palette[shortcut]?.file, "wrong palette picture for $shortcut")
        }

        assertEquals(expectedPalette.getValue(":wip:"), Emotes.BY_SHORTCUT[":construction:"]?.file)
        assertEquals(expectedPalette.getValue(":party:"), Emotes.BY_SHORTCUT[":yay:"]?.file)
    }

    /**
     * A URL contains `:` and often `:P`-looking runs; turning those into pictures would mangle
     * links, which the chat is full of.
     */
    @Test
    fun `shortcuts inside a url are left alone`() {
        val url = "https://yumicoradio.net/x:Dy"
        val tokens = EmoteParser.parse(url)
        assertTrue(shortcutsOf(tokens).isEmpty(), "a URL was mangled into emotes: $tokens")
        assertEquals(url, textOf(tokens))
    }

    // ChatParts linkifies per Text token via MediaLinks.spans(): a URL that got split across tokens
    // would never be found, so it must arrive whole in a single Text token.
    @Test
    fun `a bare url stays one text token so it can be linkified`() {
        val url = "https://youtu.be/dQw4w9WgXcQ"
        val tokens = EmoteParser.parse(url)
        assertEquals(1, tokens.size, "url was split into $tokens")
        assertEquals(url, (tokens.single() as EmoteParser.Token.Text).value)
    }

    @Test
    fun `an empty message yields no tokens`() {
        assertEquals(emptyList(), EmoteParser.parse(""))
    }

    @Test
    fun `text is preserved exactly when nothing matches`() {
        val weird = "a:b:c ::: 3:30pm"
        assertEquals(weird, textOf(EmoteParser.parse(weird)))
    }

    @Test
    fun `every palette shortcut round-trips`() {
        Emotes.PALETTE.forEach { emote ->
            val tokens = EmoteParser.parse(emote.shortcut)
            val found = tokens.filterIsInstance<EmoteParser.Token.Emote>()
            assertEquals(1, found.size, "'${emote.shortcut}' did not parse to one emote: $tokens")
            assertEquals(emote.file, found.single().emote.file, "wrong picture for ${emote.shortcut}")
        }
    }
}
