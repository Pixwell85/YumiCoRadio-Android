// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals

/**
 * The app must talk to the same host the website's chat client talks to.
 *
 * Beta3 shipped pointing at `https://yumicoradio.net`, where `/socket.io/` is a 404 — the chat runs
 * on the stream host. The symptom was an app stuck on "Connecting…" with no error, which is a
 * miserable thing to debug from a screenshot. This pins the value to the one source of truth.
 *
 * Skipped when the website checkout is not present.
 */
class ChatServerUrlTest {

    @Test
    fun `the server url matches the website's chat client`() {
        val webClient = File(
            System.getProperty("user.home"),
            "web/yumicoradio/js/yumiChat-v2.js",
        )
        assumeTrue("website checkout not present at $webClient", webClient.isFile)

        // e.g.  const SERVER_URL = 'https://s1.yumicoradio.net';
        val declared = Regex("""SERVER_URL\s*=\s*['"]([^'"]+)['"]""")
            .find(webClient.readText())
            ?.groupValues
            ?.get(1)

        assertEquals(
            declared,
            ChatRepository.DEFAULT_URL,
            "the app points somewhere the web client does not",
        )
    }
}
