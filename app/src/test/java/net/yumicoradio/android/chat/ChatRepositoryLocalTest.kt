// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import net.yumicoradio.android.chat.model.ConnectionState
import net.yumicoradio.android.chat.model.NickState
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the real [ChatRepository] against a **local** `server-v2.js`, not production.
 *
 * The spike proved the raw Socket.IO client works; this proves the app's own code path does — which
 * is where "stuck on Connecting…" was actually happening.
 */
class ChatRepositoryLocalTest {

    private val serverDir = File(System.getProperty("user.home"), "web/yumicoradio/chat")
    private val port = 3002

    @Test
    fun `repository reaches CONNECTED and Joined against a local server`() = runBlocking {
        val server = File(serverDir, "server-v2.js")
        assumeTrue("chat server source not present at $server", server.isFile)
        assumeTrue("node not on PATH", which("node") != null)

        val proc = startServer(server)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ChatRepository(scope = scope, serverUrl = "http://127.0.0.1:$port")

        try {
            awaitServerUp(proc)
            repo.connect("RepoBot")

            val connected = withTimeoutOrNull(15_000) {
                repo.connection.first { it == ConnectionState.CONNECTED }
            }
            assertEquals(ConnectionState.CONNECTED, connected, "never reached CONNECTED")

            val joined = withTimeoutOrNull(15_000) { repo.nick.first { it is NickState.Joined } }
            assertTrue(joined is NickState.Joined, "never reached Joined, stuck at ${repo.nick.value}")

            // The MOTD is only useful if it reaches the buffer the screen renders.
            val motdShown = withTimeoutOrNull(15_000) {
                repo.state.first { s -> s.buffer(s.active).any { it.user == "MOTD" } }
            }
            assertTrue(motdShown != null, "the MOTD never reached the message buffer")

            repo.send("hello from the repository")
            val echoed = withTimeoutOrNull(15_000) {
                repo.state.first { s -> s.buffer(s.active).any { it.text == "hello from the repository" } }
            }
            assertTrue(echoed != null, "message never arrived in the buffer")

            // Leaving must say so where the user is looking, not just grey out a toolbar button.
            repo.disconnect()
            val farewell = withTimeoutOrNull(5_000) {
                repo.state.first { s ->
                    s.buffer(s.active).any { it.isSystem && it.text.contains("Disconnected") }
                }
            }
            assertTrue(farewell != null, "disconnecting printed nothing in the chat")

            // Idle, never NeedsNick: NeedsNick drives an undismissable nickname dialog, and popping
            // one the instant the user asked to leave is exactly the bug this guards.
            assertEquals(
                NickState.Idle,
                repo.nick.value,
                "disconnecting left a state that would demand a nickname",
            )
        } finally {
            repo.disconnect()
            scope.cancel()
            stopServer(proc)
        }
    }

    private fun startServer(server: File): Process =
        ProcessBuilder("node", server.absolutePath)
            .directory(serverDir)
            .apply {
                environment()["PORT"] = port.toString()
                environment()["YUMI_BOT_TOKEN"] = "spike"
            }
            .redirectOutput(File("/dev/null"))
            .redirectErrorStream(true)
            .start()

    private fun stopServer(proc: Process) {
        proc.destroy()
        proc.waitFor(5, TimeUnit.SECONDS)
        proc.destroyForcibly()
    }

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
