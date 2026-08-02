// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Test
import kotlin.test.assertEquals

class MentionParserTest {

    private val users = listOf("Bob", "Bobby", "Yumi Co", "Shiro")

    @Test
    fun `plain text with no mention is one text token`() {
        assertEquals(
            listOf(MentionParser.Token.Text("hello there")),
            MentionParser.parse("hello there", users, me = "Shiro"),
        )
    }

    @Test
    fun `a mention is split out and carries the canonical nick`() {
        val tokens = MentionParser.parse("hi @bob!", users, me = "Shiro")
        assertEquals(
            listOf(
                MentionParser.Token.Text("hi "),
                MentionParser.Token.Mention("Bob", isSelf = false),
                MentionParser.Token.Text("!"),
            ),
            tokens,
        )
    }

    @Test
    fun `longest name wins so @Bobby does not resolve to Bob`() {
        val tokens = MentionParser.parse("@Bobby hey", users, me = null)
        assertEquals(MentionParser.Token.Mention("Bobby", false), tokens.first())
    }

    @Test
    fun `a name that continues into a word is not a mention`() {
        // "@Bobster" — neither Bob nor Bobby terminates cleanly, so it stays literal.
        assertEquals(listOf(MentionParser.Token.Text("@Bobster")), MentionParser.parse("@Bobster", users, null))
    }

    @Test
    fun `nicks with spaces match`() {
        val tokens = MentionParser.parse("cc @Yumi Co please", users, me = null)
        assertEquals(MentionParser.Token.Mention("Yumi Co", false), tokens[1])
    }

    @Test
    fun `a mention of yourself is flagged, case-insensitively`() {
        val tokens = MentionParser.parse("@shiro look", users, me = "Shiro")
        assertEquals(MentionParser.Token.Mention("Shiro", isSelf = true), tokens.first())
    }

    @Test
    fun `a bare at that resolves to nobody stays literal`() {
        assertEquals(listOf(MentionParser.Token.Text("email me @ home")), MentionParser.parse("email me @ home", users, null))
    }
}
