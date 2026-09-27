// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

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
