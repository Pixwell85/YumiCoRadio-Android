package net.yumicoradio.android.schedule

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The queue proxy is a public, read-only endpoint — the same one the website polls — so hitting it
 * from a test has no side effect on the station or the chat.
 *
 * It exists because an empty queue is invisible in the UI: the schedule simply extends the current
 * programme to the end of the hour, which looks plausible and is wrong.
 */
class QueueApiTest {

    @Test
    fun `the live queue proxy returns upcoming entries`() = runBlocking {
        val api = QueueApi(OkHttpClient())
        val entries = api.fetch()

        assumeTrue("no network in this environment", entries.isNotEmpty() || hasNetwork())

        assertTrue(entries.isNotEmpty(), "the queue proxy returned nothing")
        val now = System.currentTimeMillis() / 1000
        assertTrue(
            entries.any { it.startedAt > now },
            "the queue holds no future entries; schedule would show nothing coming up",
        )
        assertTrue(
            entries.any { it.duration > 0 },
            "no entry carried a duration; blocks would have zero width and vanish",
        )
    }

    private fun hasNetwork(): Boolean = runCatching {
        java.net.InetAddress.getByName("yumicoradio.net")
        true
    }.getOrDefault(false)
}
