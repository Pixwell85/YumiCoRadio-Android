// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountRepository(
    private val api: IdentityApi,
    private val tokens: AccountTokenStore,
    private val scope: CoroutineScope,
    private val deviceLabel: String,
) {
    private val _state = MutableStateFlow(AccountSnapshot())
    val state: StateFlow<AccountSnapshot> = _state.asStateFlow()

    init { scope.launch { restore() } }

    suspend fun restore() {
        val token = tokens.load()
        if (token == null) {
            _state.value = AccountSnapshot(restoring = false)
            return
        }
        try {
            val session = api.session(token)
            val profile = api.profile(token)
            _state.value = AccountSnapshot(restoring = false, session = session, profile = profile)
        } catch (error: AccountApiException) {
            if (error.status == 401) {
                tokens.clear()
                _state.value = AccountSnapshot(restoring = false, message = error.message)
            } else {
                _state.value = AccountSnapshot(restoring = false, offline = true, message = error.message)
            }
        }
    }

    suspend fun login(identifier: String, password: String): Result<AccountSession> = operation {
        val login = api.login(identifier.trim(), password, deviceLabel)
        tokens.save(login.token)
        try {
            val session = api.session(login.token)
            val profile = api.profile(login.token)
            _state.value = AccountSnapshot(restoring = false, session = session, profile = profile)
            session
        } catch (error: Throwable) {
            tokens.clear()
            throw error
        }
    }

    suspend fun logout(): Result<Unit> = operation {
        val token = tokens.load()
        try { if (token != null) api.logout(token) } finally {
            tokens.clear()
            _state.value = AccountSnapshot(restoring = false)
        }
    }

    suspend fun refresh(): Result<AccountProfile> = authenticated { token ->
        val session = api.session(token)
        val profile = api.profile(token)
        _state.update { it.copy(session = session, profile = profile, offline = false, message = null) }
        profile
    }

    suspend fun chatTicket(): Result<ChatTicket> = authenticated(api::chatTicket)
    suspend fun devices(): Result<List<AccountDevice>> = authenticated(api::sessions)
    suspend fun revokeSession(sessionId: String): Result<Unit> = authenticated { api.revokeSession(it, sessionId) }
    suspend fun revokeOthers(): Result<Int> = authenticated(api::revokeOtherSessions)
    suspend fun recoveryCodes(password: String): Result<List<String>> =
        authenticated { api.regenerateRecoveryCodes(it, password) }
    suspend fun changePassword(current: String, replacement: String): Result<Unit> =
        authenticated { api.changePassword(it, current, replacement) }
    suspend fun changeEmail(email: String, password: String): Result<Unit> =
        authenticated { api.changeEmail(it, email, password) }
    suspend fun deleteAccount(password: String, confirmation: String): Result<Unit> = authenticated { token ->
        api.deleteAccount(token, password, confirmation)
        tokens.clear()
        _state.value = AccountSnapshot(restoring = false)
    }

    suspend fun adminStats(): Result<AdminStats> = authenticated(api::adminStats)
    suspend fun adminAccounts(page: Int): Result<AdminAccountPage> =
        authenticated { api.adminAccounts(it, page.coerceAtLeast(1)) }
    suspend fun adminRename(accountId: String, username: String, password: String): Result<String> =
        authenticated { api.adminRename(it, accountId, username.trim(), password) }
    suspend fun adminModerator(accountId: String, enabled: Boolean, password: String): Result<Boolean> =
        authenticated { api.adminModerator(it, accountId, enabled, password) }
    suspend fun adminDelete(accountId: String, password: String): Result<Unit> =
        authenticated { api.adminDelete(it, accountId, password) }

    suspend fun bearerToken(): String? = tokens.load()

    suspend fun <T> authenticated(block: suspend (String) -> T): Result<T> = operation {
        val token = tokens.load() ?: throw AccountApiException(401, "session_expired", "Session expired")
        try { block(token) } catch (error: AccountApiException) {
            if (error.status == 401) {
                tokens.clear()
                _state.value = AccountSnapshot(restoring = false, message = error.message)
            }
            throw error
        }
    }

    suspend fun register(username: String, email: String, password: String, solver: AltchaSolver): Result<Unit> = operation {
        val proof = solver.solve(api.challenge("register"))
        api.register(username, email, password, proof)
    }

    suspend fun claim(username: String, legacyPassword: String, email: String, password: String, solver: AltchaSolver): Result<Unit> = operation {
        val proof = solver.solve(api.challenge("claim"))
        api.claim(username, legacyPassword, email, password, proof)
    }

    suspend fun forgot(identifier: String, solver: AltchaSolver): Result<Unit> = operation {
        val proof = solver.solve(api.challenge("recover"))
        api.forgotPassword(identifier, proof)
    }

    suspend fun recoverWithCode(username: String, code: String, password: String): Result<Unit> =
        operation { api.recoverWithCode(username, code, password) }

    suspend fun resetPassword(resetToken: String, password: String): Result<Unit> =
        operation { api.resetPassword(resetToken, password) }

    suspend fun confirmEmail(confirmationToken: String): Result<Unit> =
        authenticated { api.confirmEmail(it, confirmationToken) }

    private suspend fun <T> operation(block: suspend () -> T): Result<T> {
        _state.update { it.copy(busy = true, message = null) }
        return runCatching { block() }
            .onFailure { error -> _state.update { it.copy(message = error.message ?: "Account request failed") } }
            .also { _state.update { state -> state.copy(busy = false) } }
    }
}
