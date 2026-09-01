// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.time.TimeSource

data class AltchaProof(val challenge: JsonObject, val solution: JsonObject) {
    fun json(): JsonObject = buildJsonObject {
        put("challenge", challenge)
        put("solution", solution)
    }
}

class AltchaSolver(
    private val parallelism: Int = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)),
    private val timeoutMs: Long = 90_000,
) {
    suspend fun solve(challenge: JsonObject, onProgress: (Int) -> Unit = {}): AltchaProof =
        withTimeout(timeoutMs) {
            val parameters = challenge["parameters"]?.jsonObject ?: invalidChallenge()
            if (parameters["algorithm"]?.jsonPrimitive?.content != "PBKDF2/SHA-256") invalidChallenge()
            val nonce = parameters.hex("nonce", 16)
            val salt = parameters.hex("salt", 16)
            val prefix = parameters["keyPrefix"]?.jsonPrimitive?.content?.lowercase()
                ?.takeIf { it.length in 2..64 && it.matches(Regex("^[0-9a-f]+$")) }
                ?: invalidChallenge()
            val cost = parameters["cost"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..100_000 }
                ?: invalidChallenge()
            val keyLength = parameters["keyLength"]?.jsonPrimitive?.intOrNull ?: 32
            if (keyLength != 32) invalidChallenge()
            val expiresAt = parameters["expiresAt"]?.jsonPrimitive?.longOrNull ?: invalidChallenge()
            if (expiresAt * 1_000L <= System.currentTimeMillis()) invalidChallenge()

            val started = TimeSource.Monotonic.markNow()
            val found = CompletableDeferred<Pair<Int, ByteArray>>()
            coroutineScope {
                val jobs = mutableListOf<Job>()
                repeat(parallelism.coerceIn(1, 8)) { worker ->
                    jobs += launch(Dispatchers.Default) {
                        var counter = worker
                        var attempts = 0
                        while (!found.isCompleted && counter >= 0) {
                            coroutineContext.ensureActive()
                            val password = nonce + ByteBuffer.allocate(4).putInt(counter).array()
                            val derived = pbkdf2Sha256(password, salt, cost)
                            if (derived.toHex().startsWith(prefix)) {
                                found.complete(counter to derived)
                                break
                            }
                            counter += parallelism
                            attempts++
                            if (attempts % 20 == 0) onProgress(counter)
                        }
                    }
                }
                val (counter, derived) = found.await()
                jobs.forEach { if (it.isActive) it.cancelAndJoin() }
                val solution = buildJsonObject {
                    put("counter", counter)
                    put("derivedKey", derived.toHex())
                    put("time", JsonPrimitive(started.elapsedNow().inWholeMilliseconds.toDouble()))
                }
                AltchaProof(challenge, solution)
            }
        }

    internal fun pbkdf2Sha256(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        require(iterations > 0)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1))
        val result = u.copyOf()
        repeat(iterations - 1) {
            u = mac.doFinal(u)
            for (i in result.indices) result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
        }
        return result
    }

    private fun JsonObject.hex(name: String, bytes: Int): ByteArray {
        val value = this[name]?.jsonPrimitive?.content?.lowercase()
            ?.takeIf { it.length == bytes * 2 && it.matches(Regex("^[0-9a-f]+$")) }
            ?: invalidChallenge()
        return ByteArray(bytes) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun invalidChallenge(): Nothing =
        throw AccountApiException(400, "invalid_altcha", AccountErrorText.forCode("invalid_altcha"))
}
