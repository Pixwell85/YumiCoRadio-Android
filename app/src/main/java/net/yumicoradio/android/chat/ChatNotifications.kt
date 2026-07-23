package net.yumicoradio.android.chat

import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ChatMessage

/**
 * Decides which chat lines deserve a background notification — the part of
 * [ChatConnectionService] that has no Android in it, so the tricky rule can be tested.
 *
 * The rule is the sibling of [NotificationPolicy] and just as easy to get wrong in a way you only
 * notice when your phone stays silent for a whole conversation: the service watches the state and
 * PM flows, and every re-emission (a colour change, a user list update) re-runs this — so it must
 * fire on genuinely new tails only, never replay the backlog, and never let one conversation evict
 * another's memory.
 */
object ChatNotifications {

    /** A line worth surfacing, tagged with its conversation key (`ch:<slug>` / `pm:<nick>`). */
    data class Pending(val key: String, val message: ChatMessage, val isPm: Boolean)

    /** What to notify now, and the seen-map to carry into the next emission. */
    data class Decision(val toNotify: List<Pending>, val seen: Map<String, String>)

    fun fingerprint(message: ChatMessage): String = "${message.user}|${message.text}"

    /** The last line of every channel and every PM thread. */
    fun tails(state: ChatState, pm: PmState): List<Pending> {
        val out = mutableListOf<Pending>()
        for (channel in ChatChannel.entries) {
            state.buffer(channel).lastOrNull()?.let { out += Pending("ch:${channel.slug}", it, isPm = false) }
        }
        pm.conversations.forEach { (nick, msgs) ->
            msgs.lastOrNull()?.let { out += Pending("pm:$nick", it, isPm = true) }
        }
        return out
    }

    /**
     * The seen-map for the buffers as they already stand — used once when the service starts so
     * turning the screen off does not replay the whole backlog as a burst of stale alerts.
     */
    fun seed(state: ChatState, pm: PmState): Map<String, String> =
        tails(state, pm).associate { it.key to fingerprint(it.message) }

    /**
     * Given what was last seen, work out what is new.
     *
     * Advances the seen pointer for *every* observed tail, notifying or not: a muted or system line
     * still counts as seen, so flipping the mode to ALL later does not retroactively buzz for a
     * message that arrived while muted — you unmuted going forward.
     */
    fun advance(
        prevSeen: Map<String, String>,
        state: ChatState,
        pm: PmState,
        mode: NotificationMode,
        me: String,
    ): Decision {
        val seen = prevSeen.toMutableMap()
        val toNotify = mutableListOf<Pending>()
        for (pending in tails(state, pm)) {
            val fingerprint = fingerprint(pending.message)
            if (seen[pending.key] == fingerprint) continue
            seen[pending.key] = fingerprint
            if (NotificationPolicy.shouldNotify(pending.message, mode, me, pending.isPm)) {
                toNotify += pending
            }
        }
        return Decision(toNotify, seen)
    }
}
