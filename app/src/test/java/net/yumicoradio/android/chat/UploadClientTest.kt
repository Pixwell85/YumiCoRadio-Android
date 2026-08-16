// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadClientTest {

    @Test
    fun `upload authentication is sent before the multipart body`() {
        val request = buildUploadRequest(
            endpoint = "https://s1.yumicoradio.net/chat/upload",
            token = "secret-token",
            filename = "sample.png",
            fileBody = byteArrayOf(0x01, 0x02).toRequestBody("image/png".toMediaType()),
        )

        assertEquals("secret-token", request.header("X-Upload-Token"))
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val wire = buffer.readUtf8()
        assertTrue(wire.contains("name=\"file\""))
        assertTrue(wire.contains("filename=\"sample.png\""))
        assertFalse(wire.contains("name=\"token\""))
        assertFalse(wire.contains("secret-token"))
    }
}
