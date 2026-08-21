// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.schedule

/**
 * The station's three programmes, mapped from AzuraCast playlist names exactly as the site's
 * `playlistToProgram()` maps them, with the colours from its `.seg-*` rules.
 */
enum class Program(
    val label: String,
    val color: Long,
    /** Fits inside a narrow block on the trackbar. */
    val short: String,
    /** The site's own legend wording. */
    val legend: String,
) {
    CITYPOP("City Pop", 0xFFFFB347, "CITY POP", "City Pop + Ad"),
    VAPORWAVE("Vaporwave", 0xFF00FFFF, "VAPOR", "Vaporwave"),
    FUTUREFUNK("Future Funk", 0xFF3C7EF7, "F. FUNK", "Future Funk");

    companion object {
        fun fromPlaylist(playlist: String?): Program = when {
            playlist == null -> FUTUREFUNK
            playlist.equals("CityPop", true) || playlist.equals("ADS", true) -> CITYPOP
            playlist.equals("Vaporwave", true) -> VAPORWAVE
            // Everything else, "Yumi" included, is the general rotation.
            else -> FUTUREFUNK
        }
    }
}

/** One track on the timeline: what it belongs to, when it started, how long it runs. */
data class ScheduleEntry(val program: Program, val startedAt: Long, val duration: Int)

/** A run of consecutive tracks from the same programme, clipped to the hour being drawn. */
data class ScheduleBlock(val program: Program, val start: Long, val end: Long) {
    fun startFraction(hourStart: Long): Float = ((start - hourStart) / 3600f).coerceIn(0f, 1f)
    fun widthFraction(hourStart: Long): Float =
        ((end - start) / 3600f).coerceIn(0f, 1f - startFraction(hourStart))
}

/**
 * Keeps elapsed observations for the current clock hour even after AzuraCast's rolling history has
 * dropped them. Future queue entries are deliberately not retained because their estimated start
 * time may move before they play.
 */
class ScheduleTimeline {
    private var activeHour: Long? = null
    private val elapsed = linkedMapOf<Long, ScheduleEntry>()

    @Synchronized
    fun update(fresh: List<ScheduleEntry>, now: Long): List<ScheduleEntry> {
        val hourStart = ScheduleBuilder.hourStart(now)
        val hourEnd = hourStart + ScheduleBuilder.HOUR
        if (activeHour != hourStart) {
            activeHour = hourStart
            elapsed.clear()
        }

        fresh.asSequence()
            .filter { it.startedAt <= now }
            .filter { it.duration > 0 && it.startedAt + it.duration > hourStart && it.startedAt < hourEnd }
            .forEach { elapsed[it.startedAt] = it }

        val future = fresh.filter { it.startedAt > now }
        return (elapsed.values + future)
            .distinctBy { it.startedAt }
            .sortedBy { it.startedAt }
    }
}

/**
 * Turns the past hour's history, the current track and the queue into the blocks drawn on the
 * trackbar.
 *
 * Pure: the arithmetic — merging, clipping, filling gaps — is the part that can be wrong, and it is
 * far easier to get right against fixed timestamps than by staring at a bar on a phone.
 */
object ScheduleBuilder {

    const val HOUR = 3600L

    /** The start of the clock hour containing [now]. */
    fun hourStart(now: Long): Long = now - (now % HOUR)

    fun blocksForHour(entries: List<ScheduleEntry>, hourStart: Long): List<ScheduleBlock> {
        val hourEnd = hourStart + HOUR

        // Clip to the hour first: this view is one hour, not a timeline, and a two-hour track would
        // otherwise stretch the bar.
        val clipped = entries
            .sortedBy { it.startedAt }
            .mapNotNull { e ->
                val start = e.startedAt.coerceAtLeast(hourStart)
                val end = (e.startedAt + e.duration).coerceAtMost(hourEnd)
                if (end <= start) null else ScheduleBlock(e.program, start, end)
            }
        if (clipped.isEmpty()) return emptyList()

        // Merge consecutive runs of the same programme into one block.
        val merged = mutableListOf<ScheduleBlock>()
        clipped.forEach { block ->
            val last = merged.lastOrNull()
            if (last != null && last.program == block.program) {
                merged[merged.lastIndex] = last.copy(end = maxOf(last.end, block.end))
            } else {
                merged += block
            }
        }

        // Programme starts are the reliable boundaries. Durations from history and the queue can
        // overlap by a few seconds after rounding; chaining each block to the previous duration can
        // therefore shrink the next programme to zero. Use the next observed programme start as the
        // shared boundary instead. This also closes genuine data gaps because the station is never
        // silent.
        return merged.mapIndexedNotNull { index, block ->
            val start = if (index == 0) hourStart else block.start
            val end = merged.getOrNull(index + 1)?.start ?: hourEnd
            if (end <= start) null else block.copy(start = start, end = end)
        }
    }

    /**
     * What starts next: the programme, when it starts, and when it ends if that is actually known.
     *
     * [end] is null when the slot comes from the fixed grid rather than from the queue — the grid
     * says what starts, never how long it runs, and guessing would be inventing.
     */
    data class Slot(val program: Program, val start: Long, val end: Long? = null)

    /**
     * The next programme change after [now], taken from the unclipped timeline.
     *
     * Deliberately not built on [blocksForHour]: clipping to the hour would report a slot starting
     * at 23:59 as ending at midnight, which is the clipping showing through rather than the truth.
     */
    fun upcoming(entries: List<ScheduleEntry>, now: Long): Slot {
        val ahead = entries.filter { it.startedAt + it.duration > now }.sortedBy { it.startedAt }
        val current = ahead.firstOrNull { now >= it.startedAt }?.program
        val nextIndex = ahead.indexOfFirst { it.startedAt > now && it.program != current }
        if (nextIndex < 0) return nextSlot(now)

        val next = ahead[nextIndex]
        // Walk the consecutive run so the end is the programme's end, not one track's.
        var end = next.startedAt + next.duration
        for (entry in ahead.drop(nextIndex + 1)) {
            if (entry.program != next.program) break
            end = entry.startedAt + entry.duration
        }
        return Slot(next.program, next.startedAt, end)
    }

    /**
     * The next scheduled special, from the station's fixed grid: City Pop on the hour, Vaporwave at
     * half past, Future Funk filling the rest.
     *
     * The queue only reaches 25 tracks, and once the hour's last transition has passed there is
     * nothing ahead in it — which is most of the hour. Falling back to the grid is what keeps
     * Coming Up meaningful instead of blank, and it is what the site does.
     */
    fun nextSlot(now: Long): Slot {
        val hour = hourStart(now)
        val half = hour + 1800
        return if (now < half) Slot(Program.VAPORWAVE, half) else Slot(Program.CITYPOP, hour + HOUR)
    }

    /** Where the playhead sits across the hour, clamped so a late clock cannot draw off the end. */
    fun playheadFraction(now: Long, hourStart: Long): Float =
        ((now - hourStart) / 3600f).coerceIn(0f, 1f)
}
