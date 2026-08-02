// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ConnectionState
import io.socket.client.IO
import org.json.JSONArray
import org.json.JSONObject
import net.yumicoradio.android.chat.model.NickState
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

/**
 * The reserved-nickname handshake, end to end: rejected → password → joined.
 *
 * This is the path the primary user hits on every launch, since `Shiro` and `Yumi` are reserved on
 * production — and it is the one path that beta3 shipped without ever exercising.
 *
 * It runs against a **disposable copy** of the chat server in a temp directory, seeded with its own
 * reserved-nick file. Production is never contacted, and nothing under the website checkout is
 * modified: the real `data/reserved-nicks.json` is left alone.
 */
class ReservedNickFlowTest {

    private val chatDir = File(System.getProperty("user.home"), "web/yumicoradio/chat")
    private val port = 3003
    private val password = "correct horse battery staple"

    @Test
    fun `a reserved nickname asks for a password, then joins with the right one`() = runBlocking {
        assumeTrue("chat server source not present", File(chatDir, "server-v2.js").isFile)
        assumeTrue("node not on PATH", which("node") != null)
        assumeTrue("server dependencies not installed", File(chatDir, "node_modules").isDirectory)

        val sandbox = createSandbox()
        val proc = startServer(sandbox)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ChatRepository(scope = scope, serverUrl = "http://127.0.0.1:$port")

        try {
            awaitServerUp(proc)
            repo.connect("Yumi")

            val needsPassword = withTimeoutOrNull(15_000) {
                repo.nick.first { it is NickState.NeedsPassword }
            }
            assertTrue(
                needsPassword is NickState.NeedsPassword,
                "reserved nickname did not ask for a password; stuck at ${repo.nick.value}",
            )

            repo.submitPassword("Yumi", password)

            val joined = withTimeoutOrNull(15_000) { repo.nick.first { it is NickState.Joined } }
            assertTrue(
                joined is NickState.Joined,
                "correct password did not join; stuck at ${repo.nick.value}",
            )

            // The MOTD arrives on `motd-all`, right after `joined`. A reserved nick sees it later
            // than an open one — after the password round trip — but it must still arrive.
            val motd = withTimeoutOrNull(10_000) {
                repo.state.first { it.buffer(ChatChannel.GENERAL).any { m -> m.isMotd } }
            }
            assertTrue(
                motd != null,
                "the MOTD never reached the general buffer; buffer was " +
                    "${repo.state.value.buffer(ChatChannel.GENERAL).map { it.user to it.text }}",
            )
        } finally {
            repo.disconnect()
            scope.cancel()
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
            deleteSandbox(sandbox)
        }
    }

    /**
     * A reserved nickname survives losing the connection.
     *
     * This is the upload bug's real mechanism. Nothing in the app disconnects when it goes to the
     * background, but the OS freezes the process while the file picker is open, the socket dies,
     * and socket.io reconnects by itself — replaying `join` on `EVENT_CONNECT`. If the password was
     * discarded after the first join, that replay arrives without one and the server rejects it
     * (`server-v2.js:905`, `checkReservedNick` returns false for a non-string password). The user
     * comes back from the picker already thrown out, so the upload fails.
     *
     * The server is killed and restarted rather than the socket being poked, because that is a real
     * disconnect over the real transport. Restarting also clears the server's session table, which
     * isolates this from the separate "nickname still held by the ghost socket" rejection.
     */
    @Test
    fun `a reserved nickname rejoins by itself after the connection drops`() = runBlocking {
        assumeTrue("chat server source not present", File(chatDir, "server-v2.js").isFile)
        assumeTrue("node not on PATH", which("node") != null)
        assumeTrue("server dependencies not installed", File(chatDir, "node_modules").isDirectory)

        val sandbox = createSandbox()
        var proc = startServer(sandbox)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ChatRepository(scope = scope, serverUrl = "http://127.0.0.1:$port")

        try {
            awaitServerUp(proc)
            repo.connect("Yumi")
            withTimeoutOrNull(15_000) { repo.nick.first { it is NickState.NeedsPassword } }
            repo.submitPassword("Yumi", password)
            assertTrue(
                withTimeoutOrNull(15_000) { repo.nick.first { it is NickState.Joined } } != null,
                "setup failed: never joined in the first place",
            )

            // The drop.
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
            assertTrue(
                withTimeoutOrNull(15_000) {
                    repo.connection.first { it == ConnectionState.DISCONNECTED }
                } != null,
                "client never noticed the server had gone",
            )

            proc = startServer(sandbox)
            awaitServerUp(proc)

            // Waiting on `nick` here would prove nothing: it still reads Joined from before the
            // drop — the disconnect handler only touches `connection` — so a `first { Joined }`
            // matches instantly whether or not a rejoin ever happens. Wait for the transport, give
            // the replayed join time to be answered, then read the state that answer left behind.
            assertTrue(
                withTimeoutOrNull(60_000) {
                    repo.connection.first { it == ConnectionState.CONNECTED }
                } != null,
                "client never reconnected to the restarted server",
            )
            delay(5_000)
            assertTrue(
                repo.nick.value is NickState.Joined,
                "the replayed join was refused; ended at ${repo.nick.value}",
            )
        } finally {
            repo.disconnect()
            scope.cancel()
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
            deleteSandbox(sandbox)
        }
    }

    /**
     * The reset path: an admin hands a reserved slot to a user connected under a *different* nick.
     * `Bob` is reserved; the owner forgot the password and came back as `Bob_`. The admin reassigns
     * the `Bob` slot to them, they set a new password, and the client must re-join **as `Bob`** —
     * not stay reporting `Bob_`. This is the one flow where the joined nick differs from the one the
     * socket was opened with, and nothing else in the suite exercises it.
     */
    @Test
    fun `a slot reset re-joins under the reserved nick, and a reconnect keeps it`() = runBlocking {
        assumeTrue("chat server source not present", File(chatDir, "server-v2.js").isFile)
        assumeTrue("node not on PATH", which("node") != null)
        assumeTrue("server dependencies not installed", File(chatDir, "node_modules").isDirectory)

        val adminSalt = "00112233445566778899aabbccddeeff"
        val adminHash = scryptHex("adminpassword", adminSalt)
        val bobSalt = "ffeeddccbbaa99887766554433221100"
        val bobOldHash = scryptHex("oldbobpassword", bobSalt)
        val sandbox = createSandbox(
            """{"Yumi":{"hash":"$adminHash","salt":"$adminSalt","role":"admin"},""" +
                """"Bob":{"hash":"$bobOldHash","salt":"$bobSalt","role":"voice"}}""",
        )
        val proc = startServer(sandbox)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repo = ChatRepository(scope = scope, serverUrl = "http://127.0.0.1:$port")

        val admin = IO.socket("http://127.0.0.1:$port")
        try {
            awaitServerUp(proc)

            // The admin, capable of reserving. Emit the join from the connect callback so it never
            // races the transport, and surface a rejection instead of waiting out the timeout.
            val adminJoined = java.util.concurrent.CountDownLatch(1)
            val adminRejected = arrayOfNulls<String>(1)
            admin.on("joined") { adminJoined.countDown() }
            admin.on("nick-rejected") { args ->
                adminRejected[0] = (args.firstOrNull() as? JSONObject)?.optString("reason")
                adminJoined.countDown()
            }
            admin.on(io.socket.client.Socket.EVENT_CONNECT) {
                admin.emit(
                    "join",
                    JSONObject()
                        .put("nickname", "Yumi").put("password", "adminpassword")
                        .put("caps", JSONArray().put("reserve-v1")),
                )
            }
            admin.connect()
            assertTrue(
                adminJoined.await(15, TimeUnit.SECONDS),
                "admin never answered (connected=${admin.connected()})",
            )
            assertTrue(adminRejected[0] == null, "admin join rejected: ${adminRejected[0]}")

            // The locked-out owner, back under a fallback nick.
            repo.connect("Bob_")
            assertTrue(
                withTimeoutOrNull(15_000) {
                    repo.nick.first { it is NickState.Joined && it.nickname == "Bob_" }
                } != null,
                "Bob_ never joined; at ${repo.nick.value}",
            )

            // The admin hands the Bob slot to whoever is connected as Bob_.
            admin.emit("reserve-nick", JSONObject().put("user", "Bob_").put("slot", "Bob"))
            val setting = withTimeoutOrNull(15_000) {
                repo.nick.first { it is NickState.SettingPassword }
            } as? NickState.SettingPassword
            assertTrue(setting != null, "no set-password prompt; at ${repo.nick.value}")
            assertTrue(setting.slot == "Bob", "prompt named ${setting.slot}, expected Bob")

            // Owner chooses a new password. The client must re-join AS BOB — the bug this guards is
            // it reporting Joined("Bob_") because wire() captured the old nick.
            repo.submitReservePassword("newbobpassword")
            val joined = withTimeoutOrNull(15_000) {
                repo.nick.first { it is NickState.Joined && it.nickname == "Bob" }
            }
            assertTrue(
                joined != null,
                "reset did not land on Joined(\"Bob\"); ended at ${repo.nick.value}",
            )

            // And the auto-reconnect must replay the join as Bob with the new password, not fall
            // back to the unreserved Bob_ — otherwise a network blip drops the user off the slot.
            admin.disconnect()
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
            assertTrue(
                withTimeoutOrNull(15_000) {
                    repo.connection.first { it == ConnectionState.DISCONNECTED }
                } != null,
                "client never noticed the drop",
            )
            val proc2 = startServer(sandbox)
            try {
                awaitServerUp(proc2)
                assertTrue(
                    withTimeoutOrNull(60_000) {
                        repo.connection.first { it == ConnectionState.CONNECTED }
                    } != null,
                    "never reconnected",
                )
                delay(5_000)
                val after = repo.nick.value
                assertTrue(
                    after is NickState.Joined && after.nickname == "Bob",
                    "reconnect did not keep Bob; ended at $after",
                )
            } finally {
                proc2.destroy()
                proc2.waitFor(5, TimeUnit.SECONDS)
                proc2.destroyForcibly()
            }
        } finally {
            admin.disconnect()
            admin.close()
            repo.disconnect()
            scope.cancel()
            proc.destroy()
            proc.waitFor(5, TimeUnit.SECONDS)
            proc.destroyForcibly()
            deleteSandbox(sandbox)
        }
    }

    /**
     * Removes the sandbox without ever following a symlink.
     *
     * `File.deleteRecursively()` walks *through* a directory symlink, so an earlier version of this
     * test wiped the real `chat/node_modules` it had linked to. Symlinks are unlinked, never
     * descended into.
     */
    private fun deleteSandbox(sandbox: File) {
        java.nio.file.Files.walkFileTree(
            sandbox.toPath(),
            object : java.nio.file.SimpleFileVisitor<java.nio.file.Path>() {
                override fun visitFile(
                    file: java.nio.file.Path,
                    attrs: java.nio.file.attribute.BasicFileAttributes,
                ): java.nio.file.FileVisitResult {
                    java.nio.file.Files.deleteIfExists(file)
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: java.nio.file.Path,
                    exc: java.io.IOException?,
                ): java.nio.file.FileVisitResult {
                    java.nio.file.Files.deleteIfExists(dir)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            },
        )
    }

    /**
     * A throwaway server directory. node_modules is symlinked rather than copied — it is large, and
     * the sandbox only needs to resolve requires. Teardown must therefore never follow symlinks;
     * see [deleteSandbox].
     */
    /** The default seed: Yumi reserved as admin, with the shared test [password]. */
    private fun createSandbox(): File {
        val salt = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val hash = scryptHex(password, salt)
        return createSandbox("""{"Yumi":{"hash":"$hash","salt":"$salt","role":"admin"}}""")
    }

    private fun createSandbox(reservedNicksJson: String): File {
        val sandbox = File.createTempFile("yumi-chat-sandbox", "").apply {
            delete()
            mkdirs()
        }
        File(chatDir, "server-v2.js").copyTo(File(sandbox, "server-v2.js"))
        listOf("motd-v2.json", "motd.json", "package.json").forEach { name ->
            val src = File(chatDir, name)
            if (src.isFile) src.copyTo(File(sandbox, name))
        }
        // server-v2.js requires ./lib/roles; without it the sandbox server crash-loops on startup
        // and the test hangs until timeout rather than failing with a clear reason.
        File(chatDir, "lib").copyRecursively(File(sandbox, "lib"), overwrite = true)
        java.nio.file.Files.createSymbolicLink(
            File(sandbox, "node_modules").toPath(),
            File(chatDir, "node_modules").toPath(),
        )

        File(sandbox, "data").mkdirs()
        File(sandbox, "data/reserved-nicks.json").writeText(reservedNicksJson)
        return sandbox
    }

    /** Hashes exactly as the server does: `crypto.scryptSync(password, salt, 64)`. */
    private fun scryptHex(password: String, salt: String): String {
        val proc = ProcessBuilder(
            "node", "-e",
            "process.stdout.write(require('crypto')" +
                ".scryptSync(process.argv[1], process.argv[2], 64).toString('hex'))",
            password, salt,
        ).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor(10, TimeUnit.SECONDS)
        check(out.length == 128) { "unexpected scrypt output: $out" }
        return out
    }

    private fun startServer(sandbox: File): Process =
        ProcessBuilder("node", File(sandbox, "server-v2.js").absolutePath)
            .directory(sandbox)
            .apply {
                environment()["PORT"] = port.toString()
                environment()["YUMI_BOT_TOKEN"] = "spike"
            }
            .redirectOutput(File("/dev/null"))
            .redirectErrorStream(true)
            .start()

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
