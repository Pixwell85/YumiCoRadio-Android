// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Connectivity spike: proves the Socket.IO Java client speaks the live chat server's protocol
 * before any chat UI exists.
 *
 * Runs against a **local** `server-v2.js` on port 3001, never production — a production join
 * broadcasts "<nick> joined the chat" into the live web chat and the host's Telegram bridge, which
 * is not an acceptable side effect for a smoke test.
 *
 * Skipped (not failed) when the chat server source or `node` is absent, so the suite still passes
 * on a machine that only has the Android repo checked out.
 */
class SocketSpikeTest {

    private val serverDir = File(System.getProperty("user.home"), "web/yumicoradio/chat")
    private val port = 3001

    @Test
    fun `client joins the local chat server and round-trips a message`() {
        val server = File(serverDir, "server-v2.js")
        assumeTrue("chat server source not present at $server", server.isFile)
        assumeTrue("node not on PATH", which("node") != null)

        val proc = ProcessBuilder("node", server.absolutePath)
            .directory(serverDir)
            .apply {
                environment()["PORT"] = port.toString()
                environment()["YUMI_BOT_TOKEN"] = "spike"
            }
            // Redirect.DISCARD is missing from the android.jar stubs these tests compile against.
            .redirectOutput(File("/dev/null"))
            .redirectErrorStream(true)
            .start()

        val socket = IO.socket(
            URI.create("http://127.0.0.1:$port"),
            IO.Options.builder().setTransports(arrayOf("websocket")).build(),
        )

        try {
            awaitServerUp(proc)

            val joined = CountDownLatch(1)
            val motd = CountDownLatch(1)
            val echoed = CountDownLatch(1)
            val received = AtomicReference<JSONObject>()

            socket.on(Socket.EVENT_CONNECT) { socket.emit("join", JSONObject().put("nickname", "SpikeBot")) }
            socket.on("joined") { joined.countDown() }
            socket.on("motd-all") { motd.countDown() }
            socket.on("message") { args ->
                val msg = args.firstOrNull() as? JSONObject ?: return@on
                // The join itself emits a System message; wait for our own text to come back.
                if (msg.optString("user") == "SpikeBot") {
                    received.set(msg)
                    echoed.countDown()
                }
            }

            socket.connect()

            assertTrue(joined.await(10, TimeUnit.SECONDS), "server never confirmed the join")
            assertTrue(motd.await(5, TimeUnit.SECONDS), "server never sent motd-all")

            socket.emit("send-message", JSONObject().put("text", "hello from the spike"))

            assertTrue(echoed.await(10, TimeUnit.SECONDS), "message never came back")
            val msg = received.get()
            assertEquals("hello from the spike", msg.getString("text"))
            // The channel tag is what the app's per-channel buffers will route on.
            assertTrue(msg.has("channel"), "message carried no channel tag: $msg")
        } finally {
            socket.close()
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
        }
    }

    @Test
    fun `a channel switch is accepted and messages carry the new channel`() {
        val server = File(serverDir, "server-v2.js")
        assumeTrue("chat server source not present at $server", server.isFile)
        assumeTrue("node not on PATH", which("node") != null)

        val proc = ProcessBuilder("node", server.absolutePath)
            .directory(serverDir)
            .apply {
                environment()["PORT"] = port.toString()
                environment()["YUMI_BOT_TOKEN"] = "spike"
            }
            .redirectOutput(File("/dev/null"))
            .redirectErrorStream(true)
            .start()

        val socket = IO.socket(
            URI.create("http://127.0.0.1:$port"),
            IO.Options.builder().setTransports(arrayOf("websocket")).build(),
        )

        try {
            awaitServerUp(proc)

            val joined = CountDownLatch(1)
            val switched = CountDownLatch(1)
            val echoed = CountDownLatch(1)
            val received = AtomicReference<JSONObject>()

            socket.on(Socket.EVENT_CONNECT) { socket.emit("join", JSONObject().put("nickname", "SpikeTwo")) }
            socket.on("joined") { joined.countDown() }
            socket.on("channel-joined") { switched.countDown() }
            socket.on("message") { args ->
                val msg = args.firstOrNull() as? JSONObject ?: return@on
                if (msg.optString("user") == "SpikeTwo") {
                    received.set(msg)
                    echoed.countDown()
                }
            }

            socket.connect()
            assertTrue(joined.await(10, TimeUnit.SECONDS), "server never confirmed the join")

            socket.emit("join-channel", JSONObject().put("channel", "music"))
            assertTrue(switched.await(5, TimeUnit.SECONDS), "server never confirmed the channel switch")

            socket.emit("send-message", JSONObject().put("text", "in music now"))
            assertTrue(echoed.await(10, TimeUnit.SECONDS), "message never came back")
            assertEquals("music", received.get().getString("channel"))
        } finally {
            socket.close()
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
        }
    }

    /** The server needs a moment to bind; poll the port rather than sleeping a guessed interval. */
    private fun awaitServerUp(proc: Process) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) error("chat server exited with code ${proc.exitValue()}")
            runCatching {
                java.net.Socket("127.0.0.1", port).close()
                return
            }
            Thread.sleep(200)
        }
        error("chat server did not open port $port within 15s")
    }

    private fun which(cmd: String): File? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .map { File(it, cmd) }
            .firstOrNull { it.canExecute() }
}
