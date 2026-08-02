// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the auto-away clock.
 *
 * Kept deliberately dull after three flaky attempts. The idle check is a single long-lived
 * heartbeat — `while (isActive) { delay(TICK); check() }` — started once on the first join and
 * cancelled on disconnect. Not a one-shot timer that is armed, cancelled and re-armed from a dozen
 * call sites: that design lost its only pending wake-up whenever the bookkeeping slipped, and
 * auto-away simply never fired. A steady loop cannot lose its wake-up, and a missed check is retried
 * one tick later, so the behaviour is self-healing.
 *
 * The clock is [android.os.SystemClock.elapsedRealtime] (injected as [now]), not wall-clock time, so
 * a time-zone or NTP correction cannot stall or trip it.
 *
 * All state moves through four short `@Synchronized` methods, because activity arrives on the main
 * thread while the heartbeat and the reconnect replay run on other threads. The decision itself lives
 * in the pure, tested [PresenceRule]; this class only schedules it and guards its state.
 */
class PresenceController(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    /** Announce a status: mirror it locally and tell the server. Called only on real transitions. */
    private val onStatus: (ChatStatus) -> Unit,
) {
    private var rule = PresenceRule()
    private var loop: Job? = null

    /**
     * A join landed. The first one of a connection starts the clock running now; every later one is
     * a socket.io auto-reconnect replay, which must leave the clock untouched — restarting it (or
     * merely re-arming a timer) was what made auto-away fire at random. The server resets every
     * re-join to online, so a reconnect re-asserts whatever status we actually hold.
     */
    @Synchronized
    fun onJoined() {
        if (loop?.isActive == true) {
            if (rule.status != ChatStatus.ONLINE) onStatus(rule.status)
            return
        }
        rule = PresenceRule(lastActivity = now())
        onStatus(ChatStatus.ONLINE)
        loop = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                checkIdle()
            }
        }
    }

    /** The user did something in the chat — clears an automatic away and restarts the countdown. */
    @Synchronized
    fun markActivity() {
        val t = rule.onActivity(now())
        rule = t.rule
        if (t.notify) onStatus(t.rule.status)
    }

    /** The user picked a status from the menu. A deliberate away sticks; see [PresenceRule]. */
    @Synchronized
    fun choose(status: ChatStatus) {
        val t = rule.onChosen(status, now())
        rule = t.rule
        if (t.notify) onStatus(t.rule.status)
    }

    /** Leaving the chat: stop the heartbeat and forget everything, so the next join starts clean. */
    @Synchronized
    fun stop() {
        loop?.cancel()
        loop = null
        rule = PresenceRule()
    }

    /**
     * One idle check. Driven by the heartbeat; `internal` so a test can step it with a fake clock
     * instead of waiting on real time.
     */
    @Synchronized
    internal fun checkIdle() {
        val t = rule.onTick(now())
        rule = t.rule
        if (t.notify) onStatus(t.rule.status)
    }

    companion object {
        /**
         * How often to re-check idleness. Small next to [PresenceRule.IDLE_MILLIS], so away lands
         * within a tick of the deadline, and cheap: it only runs while the connection (and, in the
         * background, its foreground service) is already up.
         */
        const val TICK_MS = 15_000L
    }
}
