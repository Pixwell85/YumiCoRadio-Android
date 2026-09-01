// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class AltchaSolverTest {
    @Test fun `solves server compatible PBKDF2 challenge`() = runBlocking {
        val solver = AltchaSolver(parallelism = 2, timeoutMs = 5_000)
        val nonce = ByteArray(16) { it.toByte() }
        val salt = ByteArray(16) { (it + 16).toByte() }
        val expectedCounter = 7
        val key = solver.pbkdf2Sha256(nonce + ByteBuffer.allocate(4).putInt(expectedCounter).array(), salt, 2)
        val challenge = buildJsonObject {
            put("parameters", buildJsonObject {
                put("algorithm", "PBKDF2/SHA-256")
                put("nonce", nonce.hex())
                put("salt", salt.hex())
                put("cost", 2)
                put("keyLength", 32)
                put("keyPrefix", key.hex())
                put("expiresAt", System.currentTimeMillis() / 1_000 + 60)
            })
            put("signature", "0".repeat(64))
        }
        val proof = solver.solve(challenge)
        assertEquals(expectedCounter.toString(), proof.solution["counter"]!!.jsonPrimitive.content)
        assertTrue(proof.json()["challenge"]!!.jsonObject.containsKey("signature"))
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
