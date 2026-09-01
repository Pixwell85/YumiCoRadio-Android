// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

data class AccountSession(
    val accountId: String,
    val sessionId: String,
    val username: String,
    val role: String,
    val moderator: Boolean,
    val clientType: String,
    val deviceLabel: String,
    val createdAtMs: Long,
    val lastUsedAtMs: Long,
    val idleExpiresAtMs: Long,
    val absoluteExpiresAtMs: Long,
)

data class AccountProfile(
    val accountId: String,
    val username: String,
    val email: String,
    val role: String,
    val moderator: Boolean,
    val createdAtMs: Long,
    val recoveryCodesRemaining: Int,
    val activeSessions: Int,
)

data class AccountDevice(
    val sessionId: String,
    val clientType: String,
    val deviceLabel: String,
    val createdAtMs: Long,
    val lastUsedAtMs: Long,
    val idleExpiresAtMs: Long,
    val absoluteExpiresAtMs: Long,
    val current: Boolean,
)

data class AccountLogin(
    val sessionId: String,
    val token: String,
    val idleExpiresAtMs: Long,
    val absoluteExpiresAtMs: Long,
)

data class ChatTicket(val ticket: String, val expiresAtMs: Long)

data class AdminStats(
    val total: Int,
    val active: Int,
    val pending: Int,
    val locked: Int,
    val moderators: Int,
)

data class AdminAccount(
    val accountId: String,
    val username: String,
    val role: String,
    val status: String,
    val moderator: Boolean,
    val createdAtMs: Long,
)

data class AdminAccountPage(
    val page: Int,
    val pageSize: Int,
    val totalRows: Int,
    val totalPages: Int,
    val rows: List<AdminAccount>,
)

data class AccountSnapshot(
    val restoring: Boolean = true,
    val session: AccountSession? = null,
    val profile: AccountProfile? = null,
    val offline: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val signedIn: Boolean get() = session != null
    val username: String? get() = session?.username ?: profile?.username
    val isAdmin: Boolean get() = profile?.role == "admin" || session?.role == "admin"
}

class AccountApiException(
    val status: Int,
    val code: String,
    override val message: String,
) : Exception(message)

fun canManageAccounts(role: String?): Boolean = role == "admin"

object AccountErrorText {
    private val messages = mapOf(
        "invalid_request" to "Invalid request",
        "invalid_username" to "Invalid username",
        "invalid_password" to "Password does not meet the requirements",
        "invalid_credentials" to "Username, email or password is incorrect",
        "invalid_claim" to "Nickname claim could not be verified",
        "invalid_altcha" to "Verification failed",
        "invalid_or_expired_token" to "This link is invalid or has expired",
        "invalid_recovery_code" to "Recovery code is invalid or has already been used",
        "username_unavailable" to "Username is unavailable",
        "email_unavailable" to "An account already uses this email address",
        "session_expired" to "Session expired",
        "session_not_found" to "Session not found",
        "cannot_revoke_current" to "The current device cannot be revoked here",
        "rate_limited" to "Too many attempts. Please try again later",
        "feature_unavailable" to "Account service is temporarily unavailable",
        "administrator_required" to "Administrator access required",
        "reauthentication_failed" to "Password confirmation failed",
        "cannot_modify_self" to "This action cannot be applied to your own account",
    )

    fun forCode(code: String): String = messages[code] ?: "Account service request failed"
}
