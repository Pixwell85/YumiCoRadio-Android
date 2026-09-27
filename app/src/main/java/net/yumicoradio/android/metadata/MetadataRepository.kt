// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.metadata

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.yumicoradio.android.metadata.model.NowPlaying
import net.yumicoradio.android.metadata.model.RecentTrack

class MetadataRepository(
    private val fetchSnapshot: () -> AzuraSnapshot?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val pollMs: Long = 15_000L,
) {
    private val _nowPlaying = MutableStateFlow(NowPlaying.EMPTY)
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentTrack>>(emptyList())
    val recent: StateFlow<List<RecentTrack>> = _recent.asStateFlow()

    private var pollJob: Job? = null
    private val refresh = Channel<Unit>(Channel.CONFLATED)

    // Keep metadata live for a visible app (including voting while stopped) or active playback.
    // A background chat session alone must not wake the device to fetch an unseen radio title.
    @Volatile private var playing = false
    @Volatile private var foreground = false

    fun setForeground(value: Boolean) {
        val was = foreground
        foreground = value
        if (value && !was && pollJob != null) refresh.trySend(Unit)
    }

    fun setPlaying(value: Boolean) {
        val was = playing
        playing = value
        // Resume should still refresh immediately instead of waiting for the next periodic poll.
        if (value && !was && pollJob != null) refresh.trySend(Unit)
    }

    /**
     * ICY beats the poll to a track change by up to [POLL_MS]. It carries no artwork, so rather than
     * showing a title against the previous cover we just pull a fresh snapshot early.
     */
    fun onIcyTitle(streamTitle: String?) {
        val parsed = IcyParser.parse(streamTitle) ?: return
        val cur = _nowPlaying.value
        if (parsed.artist == cur.artist && parsed.title == cur.title) return
        refresh.trySend(Unit)
    }

    fun start() {
        if (pollJob != null) return
        pollJob = scope.launch(io) {
            while (isActive) {
                if (playing || foreground) {
                    runCatching { fetchSnapshot() }.getOrNull()?.let { snap ->
                        _nowPlaying.value = snap.nowPlaying
                        _recent.value = snap.recent
                    }
                    // ICY changes and foreground/playback transitions refresh early.
                    withTimeoutOrNull(pollMs) { refresh.receive() }
                } else {
                    // Do not issue another request until the app is visible or playback resumes.
                    refresh.receive()
                }
            }
        }
    }

    fun stop() { pollJob?.cancel(); pollJob = null }
}
