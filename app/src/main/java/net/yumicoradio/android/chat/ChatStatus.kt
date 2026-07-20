package net.yumicoradio.android.chat

/**
 * The three presence states the chat server accepts (`set-status`, validated server-side against
 * exactly this list).
 *
 * The colours are the website's status LEDs (`css/chat.css`), so a person shows the same colour in
 * the app and in a browser.
 */
enum class ChatStatus(val wire: String, val label: String, val led: Long) {
    ONLINE("online", "Online", 0xFF00CC00),
    AWAY("away", "Away", 0xFFFF9900),
    BUSY("busy", "Busy", 0xFFCC0000);

    companion object {
        fun fromWire(value: String?): ChatStatus =
            entries.firstOrNull { it.wire == value } ?: ONLINE
    }
}

/**
 * When to slip into "away" on its own, and when to come back.
 *
 * Ported from the website's rule (`js/yumiChat-v2.js:225`), including the part that is easy to miss:
 * an **away the person chose themselves** is sticky. Activity clears an automatic away, but must not
 * override a deliberate one — otherwise setting yourself away and then typing would silently
 * announce you as available again.
 *
 * Busy is never touched by idleness at all: it means "do not disturb", not "not here".
 *
 * Kept as a pure state machine so the rule can be tested without a clock or a socket.
 */
data class PresenceRule(
    val status: ChatStatus = ChatStatus.ONLINE,
    /** True when [status] is AWAY because the person asked for it, rather than through idleness. */
    val manualAway: Boolean = false,
    /** When the last activity happened, in the caller's own time base. */
    val lastActivity: Long = 0L,
) {
    /** The person did something. Returns the new rule, and whether the server needs telling. */
    fun onActivity(now: Long): Transition {
        val cleared = status == ChatStatus.AWAY && !manualAway
        val next = copy(
            status = if (cleared) ChatStatus.ONLINE else status,
            lastActivity = now,
        )
        return Transition(next, notify = cleared)
    }

    /** A tick of the clock. Slips to away once [IDLE_MILLIS] have passed with nothing happening. */
    fun onTick(now: Long): Transition {
        val idle = now - lastActivity >= IDLE_MILLIS
        val shouldGoAway = idle && status == ChatStatus.ONLINE
        return if (shouldGoAway) {
            Transition(copy(status = ChatStatus.AWAY, manualAway = false), notify = true)
        } else {
            Transition(this, notify = false)
        }
    }

    /** The person picked a status from the menu. Always announced, even if unchanged. */
    fun onChosen(status: ChatStatus, now: Long): Transition = Transition(
        copy(status = status, manualAway = status == ChatStatus.AWAY, lastActivity = now),
        notify = true,
    )

    data class Transition(val rule: PresenceRule, val notify: Boolean)

    companion object {
        /** Ten minutes, as the website uses. */
        const val IDLE_MILLIS = 10 * 60 * 1000L
    }
}
