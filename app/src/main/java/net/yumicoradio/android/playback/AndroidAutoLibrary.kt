// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import androidx.media3.common.Player

/** Small, deterministic model for the Android Auto browse tree. */
internal object AndroidAutoLibrary {
    const val ROOT = "root"
    const val LIVE = "live_remembered"
    const val QUALITY_FOLDER = "stream_quality"
}

internal sealed interface AndroidAutoEntry {
    data class Play(val quality: StreamQuality) : AndroidAutoEntry
    data class Browse(val mediaId: String) : AndroidAutoEntry
}

/**
 * Keep the remembered station as the primary action. Stream quality remains available, but no
 * longer appears as three competing programmes every time Android Auto opens the app.
 */
internal fun androidAutoChildren(parentId: String, remembered: StreamQuality): List<AndroidAutoEntry> =
    when (parentId) {
        AndroidAutoLibrary.ROOT -> listOf(
            AndroidAutoEntry.Play(remembered),
            AndroidAutoEntry.Browse(AndroidAutoLibrary.QUALITY_FOLDER),
        )
        AndroidAutoLibrary.QUALITY_FOLDER -> StreamQuality.entries.map(AndroidAutoEntry::Play)
        else -> emptyList()
    }

internal fun androidAutoQualityFor(mediaId: String?, remembered: StreamQuality): StreamQuality =
    if (mediaId == AndroidAutoLibrary.LIVE) remembered else StreamQuality.fromMediaId(mediaId)

/** Keep the car's Play control available without starting audio on connection. */
internal fun shouldPrepareCarSession(
    isCarController: Boolean,
    playbackState: Int,
    hasPlayableItem: Boolean,
): Boolean = isCarController && playbackState == Player.STATE_IDLE && hasPlayableItem

/** Legacy Android Auto can browse with our own compat package rather than its car package. */
internal fun shouldPrepareBrowseSession(
    isCarBrowser: Boolean,
    playbackState: Int,
    hasPlayableItem: Boolean,
): Boolean = isCarBrowser && playbackState == Player.STATE_IDLE && hasPlayableItem
