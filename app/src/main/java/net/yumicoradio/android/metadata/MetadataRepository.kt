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
                runCatching { api.fetch() }.getOrNull()?.let { snap ->
                    _nowPlaying.value = snap.nowPlaying
                    _recent.value = snap.recent
                }
                withTimeoutOrNull(POLL_MS) { refresh.receive() }
            }
        }
    }

    fun stop() { pollJob?.cancel(); pollJob = null }

    private companion object { const val POLL_MS = 15_000L }
}
