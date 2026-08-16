// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.ChatAutocomplete.Suggestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAutocompleteTest {
    private val users = listOf("Bob", "Bobby", "Alice", "Cool Dude")

    private fun emotes(r: ChatAutocomplete.Result?) =
        r!!.suggestions.filterIsInstance<Suggestion.Emote>().map { it.emote.shortcut }

    private fun mentions(r: ChatAutocomplete.Result?) =
        r!!.suggestions.filterIsInstance<Suggestion.Mention>().map { it.nick }

    @Test
    fun `at-mention matches nicknames by prefix`() {
        val r = ChatAutocomplete.suggest("hey @bo", users, me = "Alice")
        assertEquals(listOf("Bob", "Bobby"), mentions(r))
        assertEquals("hey ".length, r!!.triggerStart) // the '@' position
    }

    @Test
    fun `at-mention excludes your own nick`() {
        val r = ChatAutocomplete.suggest("@ali", users, me = "Alice")
        assertNull(r) // Alice is the only match and it is us → nothing to suggest
    }

    @Test
    fun `at-mention resolves a nick containing a space`() {
        val r = ChatAutocomplete.suggest("wave @Cool D", users, me = "Bob")
        assertEquals(listOf("Cool Dude"), mentions(r))
    }

    @Test
    fun `colon triggers emote shortcuts by inner prefix`() {
        val r = ChatAutocomplete.suggest("nice :sm", users, me = null)
        val got = emotes(r)
        assertTrue("expected :smirk: among $got", got.contains(":smirk:"))
        // every suggestion is a colon emote whose inner text starts with "sm"
        assertTrue(got.all { it.startsWith(":") && inner(it).startsWith("sm", ignoreCase = true) })
    }

    @Test
    fun `colon strips a trailing colon when matching`() {
        val r = ChatAutocomplete.suggest("aa :neu", users, me = null)
        assertTrue(emotes(r).contains(":neutral:"))
    }

    @Test
    fun `new website emotes and aliases are suggested`() {
        assertTrue(emotes(ChatAutocomplete.suggest(":wip", users, me = null)).contains(":wip:"))
        assertTrue(emotes(ChatAutocomplete.suggest(":party", users, me = null)).contains(":party:"))
        assertTrue(emotes(ChatAutocomplete.suggest(":announce", users, me = null)).contains(":announce:"))
        assertTrue(emotes(ChatAutocomplete.suggest(":construction", users, me = null)).contains(":construction:"))
        assertTrue(emotes(ChatAutocomplete.suggest(":yay", users, me = null)).contains(":yay:"))
    }

    @Test
    fun `trailing symbolic token matches a special emote immediately`() {
        val r = ChatAutocomplete.suggest("lol ^^", users, me = null)
        assertTrue(emotes(r).contains("^^"))
    }

    @Test
    fun `a one-char plain word does not trigger`() {
        assertNull(ChatAutocomplete.suggest("h", users, me = null))
    }

    @Test
    fun `plain text with no trigger yields nothing`() {
        assertNull(ChatAutocomplete.suggest("hello there", users, me = null))
    }

    @Test
    fun `apply replaces the trigger token with an at-mention and a space`() {
        val r = ChatAutocomplete.suggest("hey @bo", users, me = "Alice")!!
        val out = ChatAutocomplete.apply("hey @bo", r.triggerStart, Suggestion.Mention("Bobby"))
        assertEquals("hey @Bobby ", out)
    }

    @Test
    fun `apply replaces the trigger token with an emote and a space`() {
        val r = ChatAutocomplete.suggest("nice :sm", users, me = null)!!
        val smirk = r.suggestions.filterIsInstance<Suggestion.Emote>().first { it.emote.shortcut == ":smirk:" }
        val out = ChatAutocomplete.apply("nice :sm", r.triggerStart, smirk)
        assertEquals("nice :smirk: ", out)
    }

    @Test
    fun `an empty query after the trigger does not fire`() {
        assertNull(ChatAutocomplete.suggest("x :", users, me = null)) // lone ':'
        assertNull(ChatAutocomplete.suggest("x @", users, me = null)) // lone '@'
    }

    @Test
    fun `suggestions are capped at MAX`() {
        val many = (1..12).map { "amy$it" }            // 12 nicks all starting "amy"
        val r = ChatAutocomplete.suggest("@amy", many, me = null)
        assertEquals(ChatAutocomplete.MAX, mentions(r).size)
    }

    private fun inner(shortcut: String): String {
        val body = shortcut.removePrefix(":")
        return if (body.endsWith(":")) body.dropLast(1) else body
    }
}
