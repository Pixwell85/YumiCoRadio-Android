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
    private val api: AzuraNowPlayingApi,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _nowPlaying = MutableStateFlow(NowPlaying.EMPTY)
    val nowPlaying: StateFlow<NowPlaying> = _nowPlaying.asStateFlow()

    private val _recent = MutableStateFlow<List<RecentTrack>>(emptyList())
    val recent: StateFlow<List<RecentTrack>> = _recent.asStateFlow()

    private var pollJob: Job? = null
    private val refresh = Channel<Unit>(Channel.CONFLATED)

    // Only poll while audio is actually playing. Paused, the station plays on without us, so a live
    // readout means nothing — and a 45s network wake every 45s for a screen nobody is looking at is
    // pure battery cost. The service pushes play/pause here; resuming fetches at once.
    @Volatile private var playing = true

    fun setPlaying(value: Boolean) {
        val was = playing
        playing = value
        // Only ping on pause->resume. `refresh` is CONFLATED, so a paused-branch receive must never
        // race a pause token against a resume token (the second would coalesce the first away and the
        // loop could park with playing == true). Sending solely on resume makes a paused-branch wake
        // unambiguously mean "resume". Pausing just lets the current wait expire, then the loop parks.
        if (value && !was) refresh.trySend(Unit)
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
                if (playing) {
                    runCatching { api.fetch() }.getOrNull()?.let { snap ->
                        _nowPlaying.value = snap.nowPlaying
                        _recent.value = snap.recent
                    }
                    // Wake early on an ICY track change, otherwise poll again after POLL_MS.
                    withTimeoutOrNull(POLL_MS) { refresh.receive() }
                } else {
                    // Paused: stop polling and block until playback resumes (setPlaying pings this).
                    refresh.receive()
                }
            }
        }
    }

    fun stop() { pollJob?.cancel(); pollJob = null }

    // ICY already fires an early refresh on every track change, so this timed poll is really just the
    // artwork fallback — 45s is plenty and a third of the network wakes the old 15s cost while playing.
    private companion object { const val POLL_MS = 45_000L }
}
