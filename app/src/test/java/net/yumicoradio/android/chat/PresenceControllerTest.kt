// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Drives [PresenceController] with a fake clock and steps its idle check by hand, so the exact
 * wiring that broke across three betas is pinned without waiting on real time. The scheduling loop
 * itself is trivial (`delay(TICK); checkIdle()`); what matters is that each entry point moves the
 * pure rule correctly and announces only real transitions.
 */
class PresenceControllerTest {
    private var clock = 0L
    private val pushed = mutableListOf<ChatStatus>()
    private lateinit var scope: CoroutineScope
    private lateinit var presence: PresenceController

    private val idle = PresenceRule.IDLE_MILLIS

    @Before
    fun setUp() {
        clock = 0L
        pushed.clear()
        // Unconfined so onJoined's launched loop starts and suspends at its first delay, leaving the
        // Job active — which is how a reconnect is told apart from a first join.
        scope = CoroutineScope(Dispatchers.Unconfined)
        presence = PresenceController(scope, now = { clock }, onStatus = { pushed += it })
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `first join announces online then slips to away once idle`() {
        presence.onJoined()
        assertEquals(listOf(ChatStatus.ONLINE), pushed)

        clock = idle
        presence.checkIdle()
        assertEquals(listOf(ChatStatus.ONLINE, ChatStatus.AWAY), pushed)
    }

    @Test
    fun `activity within the window keeps you online`() {
        presence.onJoined()
        clock = 5 * 60_000
        presence.markActivity()                 // still online → nothing announced
        clock = 5 * 60_000 + idle - 1
        presence.checkIdle()                     // only idle-1 since activity → not away
        assertEquals(listOf(ChatStatus.ONLINE), pushed)
    }

    @Test
    fun `a reconnect does not restart the idle clock`() {
        presence.onJoined()
        clock = idle                             // deadline reached
        presence.onJoined()                      // reconnect replay just before the check
        presence.checkIdle()
        // If the reconnect had reset lastActivity, this would still read online.
        assertEquals(listOf(ChatStatus.ONLINE, ChatStatus.AWAY), pushed)
    }

    @Test
    fun `a reconnect re-asserts a held away`() {
        presence.onJoined()
        clock = idle
        presence.checkIdle()                     // AWAY (auto)
        pushed.clear()

        presence.onJoined()                      // server reset us to online; we push away back
        assertEquals(listOf(ChatStatus.AWAY), pushed)
    }

    @Test
    fun `a manual away sticks through later activity`() {
        presence.onJoined()
        presence.choose(ChatStatus.AWAY)
        clock += 1_000
        presence.markActivity()                  // must not silently announce you online again
        assertEquals(listOf(ChatStatus.ONLINE, ChatStatus.AWAY), pushed)
    }

    @Test
    fun `busy is never touched by idleness`() {
        presence.onJoined()
        presence.choose(ChatStatus.BUSY)
        clock = idle * 2
        presence.checkIdle()
        assertEquals(listOf(ChatStatus.ONLINE, ChatStatus.BUSY), pushed)
    }

    @Test
    fun `activity clears an automatic away`() {
        presence.onJoined()
        clock = idle
        presence.checkIdle()                     // AWAY (auto)
        clock += 1_000
        presence.markActivity()                  // back online
        assertEquals(listOf(ChatStatus.ONLINE, ChatStatus.AWAY, ChatStatus.ONLINE), pushed)
    }

    @Test
    fun `stop lets the next join start a fresh clock`() {
        presence.onJoined()
        clock = idle
        presence.checkIdle()                     // AWAY
        presence.stop()
        pushed.clear()

        clock = idle + 1_000
        presence.onJoined()                      // fresh session, online again
        assertEquals(listOf(ChatStatus.ONLINE), pushed)
        presence.checkIdle()                     // only just joined → not idle yet
        assertEquals(listOf(ChatStatus.ONLINE), pushed)
    }
}
