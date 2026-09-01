// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

private val UUID = Regex("^[a-f0-9-]{36}$", RegexOption.IGNORE_CASE)
private val TOKEN = Regex("^[A-Za-z0-9_-]{32,256}$")
private val ACCOUNT_ROLES = setOf("user", "voice", "admin")

internal fun JsonObject.requiredString(name: String, max: Int = 256): String {
    val value = this[name]?.jsonPrimitive?.content ?: invalidResponse()
    if (value.isBlank() || value.length > max || value.any { it.code < 0x20 || it.code == 0x7f }) {
        invalidResponse()
    }
    return value
}

internal fun JsonObject.requiredUuid(name: String): String =
    requiredString(name, 36).takeIf(UUID::matches) ?: invalidResponse()

internal fun JsonObject.requiredLong(name: String): Long =
    this[name]?.jsonPrimitive?.longOrNull?.takeIf { it >= 0 } ?: invalidResponse()

internal fun JsonObject.requiredInt(name: String, maximum: Int = 1_000_000): Int =
    this[name]?.jsonPrimitive?.intOrNull?.takeIf { it in 0..maximum } ?: invalidResponse()

internal fun JsonObject.requiredBoolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.booleanOrNull ?: invalidResponse()

internal fun parseSession(value: JsonElement): AccountSession {
    val o = value.jsonObject
    val clientType = o.requiredString("clientType", 16)
    if (clientType != "android") invalidResponse()
    val role = o.requiredString("role", 16)
    if (role !in ACCOUNT_ROLES) invalidResponse()
    return AccountSession(
        accountId = o.requiredUuid("accountId"), sessionId = o.requiredUuid("sessionId"),
        username = o.requiredString("username", 32), role = role,
        moderator = o.requiredBoolean("moderator"), clientType = clientType,
        deviceLabel = o.requiredString("deviceLabel", 80), createdAtMs = o.requiredLong("createdAtMs"),
        lastUsedAtMs = o.requiredLong("lastUsedAtMs"), idleExpiresAtMs = o.requiredLong("idleExpiresAtMs"),
        absoluteExpiresAtMs = o.requiredLong("absoluteExpiresAtMs"),
    )
}

internal fun parseLogin(value: JsonElement): AccountLogin {
    val o = value.jsonObject
    val token = o.requiredString("token", 256)
    if (!TOKEN.matches(token)) invalidResponse()
    return AccountLogin(
        sessionId = o.requiredUuid("sessionId"), token = token,
        idleExpiresAtMs = o.requiredLong("idleExpiresAtMs"),
        absoluteExpiresAtMs = o.requiredLong("absoluteExpiresAtMs"),
    )
}

internal fun parseProfile(value: JsonElement): AccountProfile {
    val o = value.jsonObject
    val role = o.requiredString("role", 16)
    if (role !in ACCOUNT_ROLES) invalidResponse()
    return AccountProfile(
        accountId = o.requiredUuid("accountId"), username = o.requiredString("username", 32),
        email = o.requiredString("email", 254), role = role, moderator = o.requiredBoolean("moderator"),
        createdAtMs = o.requiredLong("createdAtMs"),
        recoveryCodesRemaining = o.requiredInt("recoveryCodesRemaining", 100),
        activeSessions = o.requiredInt("activeSessions", 1_000),
    )
}

internal fun parseDevices(value: JsonElement): List<AccountDevice> {
    val values = value.jsonObject["sessions"]?.jsonArray ?: invalidResponse()
    if (values.size > 100) invalidResponse()
    return values.map { raw ->
        val o = raw.jsonObject
        val clientType = o.requiredString("clientType", 16)
        if (clientType !in setOf("android", "browser")) invalidResponse()
        AccountDevice(
            sessionId = o.requiredUuid("sessionId"), clientType = clientType,
            deviceLabel = o.requiredString("deviceLabel", 80), createdAtMs = o.requiredLong("createdAtMs"),
            lastUsedAtMs = o.requiredLong("lastUsedAtMs"), idleExpiresAtMs = o.requiredLong("idleExpiresAtMs"),
            absoluteExpiresAtMs = o.requiredLong("absoluteExpiresAtMs"), current = o.requiredBoolean("current"),
        )
    }
}

internal fun parseChatTicket(value: JsonElement): ChatTicket {
    val o = value.jsonObject
    val ticket = o.requiredString("ticket", 64)
    if (!Regex("^[A-Za-z0-9_-]{43}$").matches(ticket)) invalidResponse()
    return ChatTicket(ticket, o.requiredLong("expiresAtMs"))
}

internal fun parseRecoveryCodes(value: JsonElement): List<String> {
    val values: JsonArray = value.jsonObject["recoveryCodes"]?.jsonArray ?: invalidResponse()
    if (values.isEmpty() || values.size > 20) invalidResponse()
    return values.map { it.jsonPrimitive.content.takeIf { code -> code.length in 8..64 } ?: invalidResponse() }
}

internal fun parseAdminStats(value: JsonElement): AdminStats {
    val o = value.jsonObject
    return AdminStats(o.requiredInt("total"), o.requiredInt("active"), o.requiredInt("pending"),
        o.requiredInt("locked"), o.requiredInt("moderators"))
}

internal fun parseAdminPage(value: JsonElement): AdminAccountPage {
    val o = value.jsonObject
    val rows = o["rows"]?.jsonArray ?: invalidResponse()
    if (rows.size > 100) invalidResponse()
    return AdminAccountPage(
        page = o.requiredInt("page").takeIf { it >= 1 } ?: invalidResponse(),
        pageSize = o.requiredInt("pageSize", 100).takeIf { it >= 1 } ?: invalidResponse(),
        totalRows = o.requiredInt("totalRows"), totalPages = o.requiredInt("totalPages"),
        rows = rows.map { raw ->
            val row = raw.jsonObject
            val role = row.requiredString("role", 16)
            if (role !in ACCOUNT_ROLES) invalidResponse()
            AdminAccount(row.requiredUuid("accountId"), row.requiredString("username", 32), role,
                row.requiredString("status", 16), row.requiredBoolean("moderator"), row.requiredLong("createdAtMs"))
        },
    )
}

private fun invalidResponse(): Nothing =
    throw AccountApiException(502, "invalid_response", "Account service returned invalid data")
