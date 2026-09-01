// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder

class IdentityApi(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://s1.yumicoradio.net/account",
) {
    suspend fun status() = request("/v1/status")

    suspend fun login(identifier: String, password: String, deviceLabel: String): AccountLogin =
        parseLogin(request("/v1/login", "POST", buildJsonObject {
            put("identifier", identifier)
            put("password", password)
            put("remember", true)
            put("clientType", "android")
            put("deviceLabel", deviceLabel.take(80))
        }))

    suspend fun session(token: String): AccountSession = parseSession(request("/v1/session", token = token))
    suspend fun profile(token: String): AccountProfile = parseProfile(request("/v1/profile", token = token))
    suspend fun sessions(token: String): List<AccountDevice> = parseDevices(request("/v1/sessions", token = token))

    suspend fun usernameAvailability(username: String): String =
        request("/v1/usernames/availability?username=${encode(username)}")
            .jsonObject.requiredString("status", 16)

    suspend fun challenge(purpose: String): JsonObject =
        request("/v1/altcha/challenge?purpose=${encode(purpose)}").jsonObject

    suspend fun register(username: String, email: String, password: String, proof: AltchaProof) {
        request("/v1/register", "POST", buildJsonObject {
            put("username", username); put("email", email); put("password", password); put("altcha", proof.json())
        })
    }

    suspend fun claim(username: String, legacyPassword: String, email: String, password: String, proof: AltchaProof) {
        request("/v1/claim", "POST", buildJsonObject {
            put("username", username); put("legacyPassword", legacyPassword); put("email", email)
            put("password", password); put("altcha", proof.json())
        })
    }

    suspend fun forgotPassword(identifier: String, proof: AltchaProof) {
        request("/v1/password/forgot", "POST", buildJsonObject {
            put("identifier", identifier); put("altcha", proof.json())
        })
    }

    suspend fun resetPassword(token: String, newPassword: String) {
        request("/v1/password/reset", "POST", buildJsonObject {
            put("token", token); put("newPassword", newPassword)
        })
    }

    suspend fun recoverWithCode(username: String, code: String, newPassword: String) {
        request("/v1/password/recover-with-code", "POST", buildJsonObject {
            put("username", username); put("code", code); put("newPassword", newPassword)
        })
    }

    suspend fun logout(token: String) { request("/v1/logout", "POST", buildJsonObject {}, token) }
    suspend fun changePassword(token: String, currentPassword: String, newPassword: String) {
        request("/v1/profile/password/change", "POST", buildJsonObject {
            put("currentPassword", currentPassword); put("newPassword", newPassword)
        }, token)
    }

    suspend fun changeEmail(token: String, newEmail: String, password: String) {
        request("/v1/profile/email/change", "POST", buildJsonObject {
            put("newEmail", newEmail); put("password", password)
        }, token)
    }

    suspend fun confirmEmail(token: String, confirmationToken: String) {
        request("/v1/profile/email/confirm", "POST", buildJsonObject { put("token", confirmationToken) }, token)
    }

    suspend fun revokeSession(token: String, sessionId: String) {
        request("/v1/sessions/${encode(sessionId)}", "DELETE", buildJsonObject {}, token)
    }

    suspend fun revokeOtherSessions(token: String): Int =
        request("/v1/sessions/revoke-others", "POST", buildJsonObject {}, token)
            .jsonObject.requiredInt("revoked", 1_000)

    suspend fun regenerateRecoveryCodes(token: String, password: String): List<String> =
        parseRecoveryCodes(request("/v1/recovery-codes/regenerate", "POST",
            buildJsonObject { put("password", password) }, token))

    suspend fun deleteAccount(token: String, password: String, confirmation: String) {
        request("/v1/account", "DELETE", buildJsonObject {
            put("password", password); put("confirmation", confirmation)
        }, token)
    }

    suspend fun chatTicket(token: String): ChatTicket =
        parseChatTicket(request("/v1/chat-ticket", "POST", buildJsonObject {}, token))

    suspend fun adminStats(token: String): AdminStats = parseAdminStats(request("/v1/admin/stats", token = token))
    suspend fun adminAccounts(token: String, page: Int): AdminAccountPage =
        parseAdminPage(request("/v1/admin/accounts?page=$page", token = token))

    suspend fun adminRename(token: String, accountId: String, username: String, password: String): String =
        request("/v1/admin/accounts/${encode(accountId)}/rename", "POST", buildJsonObject {
            put("username", username); put("adminPassword", password)
        }, token).jsonObject.requiredString("username", 32)

    suspend fun adminModerator(token: String, accountId: String, enabled: Boolean, password: String): Boolean =
        request("/v1/admin/accounts/${encode(accountId)}/moderator", "POST", buildJsonObject {
            put("enabled", enabled); put("adminPassword", password)
        }, token).jsonObject.requiredBoolean("moderator")

    suspend fun adminDelete(token: String, accountId: String, password: String) {
        request("/v1/admin/accounts/${encode(accountId)}", "DELETE",
            buildJsonObject { put("adminPassword", password) }, token)
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: JsonObject? = null,
        token: String? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(baseUrl + path)
            .header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: EMPTY_BODY)
            "DELETE" -> builder.delete(requestBody ?: EMPTY_BODY)
            else -> throw IllegalArgumentException("Unsupported method")
        }
        val response = try { http.newCall(builder.build()).execute() }
        catch (error: IOException) { throw AccountApiException(0, "network_error", "Could not reach account service") }
        response.use {
            val contentLength = it.body?.contentLength() ?: 0
            if (contentLength > MAX_RESPONSE_BYTES) invalidNetworkResponse()
            val text = it.body?.string().orEmpty()
            if (text.length > MAX_RESPONSE_BYTES) invalidNetworkResponse()
            val json = if (text.isBlank()) JsonNull else runCatching { JSON.parseToJsonElement(text) }
                .getOrElse { invalidNetworkResponse() }
            if (!it.isSuccessful) {
                val error = (json as? JsonObject)?.get("error") as? JsonObject
                val code = error?.get("code")?.jsonPrimitive?.content?.take(64) ?: "request_failed"
                throw AccountApiException(it.code, code, AccountErrorText.forCode(code))
            }
            json
        }
    }

    private fun invalidNetworkResponse(): Nothing =
        throw AccountApiException(502, "invalid_response", "Account service returned invalid data")

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; isLenient = false }
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = "{}".toRequestBody(JSON_MEDIA)
        private const val MAX_RESPONSE_BYTES = 262_144
    }
}
