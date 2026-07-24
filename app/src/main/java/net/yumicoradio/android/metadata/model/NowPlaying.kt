// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.metadata.model

data class NowPlaying(
    val artist: String,
    val title: String,
    val artworkUrl: String?,
    val listeners: Int,
    val online: Boolean,
    /** Unix seconds the current track started; 0 when unknown. */
    val playedAt: Long = 0L,
    /** Track length in seconds; 0 for a live broadcast with no fixed length. */
    val duration: Int = 0,
    /** The playlist AzuraCast is drawing from — the schedule groups the hour by this. */
    val playlist: String? = null,
) {
    val hasTrack get() = title.isNotBlank()
    companion object { val EMPTY = NowPlaying("", "", null, 0, false) }
}
