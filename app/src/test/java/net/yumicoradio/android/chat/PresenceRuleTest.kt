// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The auto-away rule, ported from the website (`js/yumiChat-v2.js:225`). */
class PresenceRuleTest {

    private val idle = PresenceRule.IDLE_MILLIS

    @Test
    fun `goes away after the idle timeout`() {
        val rule = PresenceRule(status = ChatStatus.ONLINE, lastActivity = 0L)
        val t = rule.onTick(idle)
        assertEquals(ChatStatus.AWAY, t.rule.status)
        assertTrue(t.notify, "the server must be told")
        assertFalse(t.rule.manualAway, "an idle away is not a chosen one")
    }

    @Test
    fun `stays online before the timeout`() {
        val rule = PresenceRule(status = ChatStatus.ONLINE, lastActivity = 0L)
        val t = rule.onTick(idle - 1)
        assertEquals(ChatStatus.ONLINE, t.rule.status)
        assertFalse(t.notify)
    }

    @Test
    fun `activity brings an idle away back to online`() {
        val away = PresenceRule(status = ChatStatus.AWAY, manualAway = false, lastActivity = 0L)
        val t = away.onActivity(idle + 5)
        assertEquals(ChatStatus.ONLINE, t.rule.status)
        assertTrue(t.notify, "coming back must be announced")
    }

    /** The bug the website's rule guards against: typing must not cancel an away you chose. */
    @Test
    fun `activity does not clear a chosen away`() {
        val away = PresenceRule(status = ChatStatus.AWAY, manualAway = true, lastActivity = 0L)
        val t = away.onActivity(idle + 5)
        assertEquals(ChatStatus.AWAY, t.rule.status, "a deliberate away is sticky")
        assertFalse(t.notify, "and nothing is announced")
    }

    @Test
    fun `busy is never touched by idleness`() {
        val busy = PresenceRule(status = ChatStatus.BUSY, lastActivity = 0L)
        val t = busy.onTick(idle * 10)
        assertEquals(ChatStatus.BUSY, t.rule.status)
        assertFalse(t.notify)
    }

    @Test
    fun `a chosen away is remembered as manual`() {
        val t = PresenceRule().onChosen(ChatStatus.AWAY, now = 100L)
        assertEquals(ChatStatus.AWAY, t.rule.status)
        assertTrue(t.rule.manualAway)
        assertTrue(t.notify)
    }

    @Test
    fun `choosing online clears the manual-away flag`() {
        val away = PresenceRule(status = ChatStatus.AWAY, manualAway = true)
        val t = away.onChosen(ChatStatus.ONLINE, now = 100L)
        assertEquals(ChatStatus.ONLINE, t.rule.status)
        assertFalse(t.rule.manualAway)
    }

    /** After coming back from an idle away, the timeout starts over rather than firing again at once. */
    @Test
    fun `activity resets the idle countdown`() {
        val rule = PresenceRule(status = ChatStatus.ONLINE, lastActivity = 0L)
        val afterActivity = rule.onActivity(idle - 10).rule
        assertEquals(ChatStatus.ONLINE, afterActivity.onTick(idle).rule.status, "clock restarts from the activity")
        assertEquals(ChatStatus.AWAY, afterActivity.onTick(idle * 2 - 10).rule.status)
    }

    @Test
    fun `unknown wire values fall back to online`() {
        assertEquals(ChatStatus.ONLINE, ChatStatus.fromWire("bogus"))
        assertEquals(ChatStatus.BUSY, ChatStatus.fromWire("busy"))
    }
}
