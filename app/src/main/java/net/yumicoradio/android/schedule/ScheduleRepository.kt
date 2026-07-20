package net.yumicoradio.android.schedule

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The upcoming queue, from the site's own PHP proxy.
 *
 * The raw AzuraCast queue endpoint needs an API key; the proxy holds it server-side and exposes the
 * same data publicly, so the app carries no credentials.
 */
class QueueApi(private val http: OkHttpClient) {

    suspend fun fetch(): List<ScheduleEntry> = withContext(Dispatchers.IO) {
        runCatching {
            // Cache-buster: the proxy sits behind Cloudflare, which would otherwise serve a stale
            // queue for minutes at a time.
            val url = "$ENDPOINT?t=${System.currentTimeMillis()}"
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                parse(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(raw: String): List<ScheduleEntry> = runCatching {
        val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
        root["tracks"]?.jsonArray.orEmpty().mapNotNull { element ->
            val track = element.jsonObject
            val playedAt = track["played_at"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            ScheduleEntry(
                program = Program.fromPlaylist(track["playlist"]?.jsonPrimitive?.contentOrNull),
                startedAt = playedAt,
                duration = track["duration"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0,
            )
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val ENDPOINT = "https://yumicoradio.net/queue-api/public-queue.php"
    }
}

/**
 * Assembles the hour shown on the schedule: past tracks from the now-playing history, the current
 * track, and the queue ahead.
 */
class ScheduleRepository(
    private val queueApi: QueueApi,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val _queue = MutableStateFlow<List<ScheduleEntry>>(emptyList())
    val queue: StateFlow<List<ScheduleEntry>> = _queue.asStateFlow()

    private var pollJob: Job? = null

    /** Polled while the schedule is on screen; there is nothing to show when it is not. */
    fun start() {
        if (pollJob != null) return
        pollJob = scope.launch(io) {
            while (isActive) {
                _queue.value = queueApi.fetch()
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private companion object {
        /** The site polls at the same rate; tracks are minutes long, so faster buys nothing. */
        const val POLL_MS = 30_000L
    }
}
