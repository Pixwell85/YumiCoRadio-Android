// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.account.AccountDevice
import net.yumicoradio.android.account.AccountSnapshot
import net.yumicoradio.android.account.AdminAccountPage
import net.yumicoradio.android.account.AdminStats
import net.yumicoradio.android.account.AltchaSolver

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as YumiApp).account
    val state: StateFlow<AccountSnapshot> = repository.state

    private val _devices = MutableStateFlow<List<AccountDevice>>(emptyList())
    val devices: StateFlow<List<AccountDevice>> = _devices.asStateFlow()

    private val _recoveryCodes = MutableStateFlow<List<String>>(emptyList())
    val recoveryCodes: StateFlow<List<String>> = _recoveryCodes.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    private val _adminStats = MutableStateFlow<AdminStats?>(null)
    val adminStats: StateFlow<AdminStats?> = _adminStats.asStateFlow()

    private val _adminAccounts = MutableStateFlow<AdminAccountPage?>(null)
    val adminAccounts: StateFlow<AdminAccountPage?> = _adminAccounts.asStateFlow()

    fun clearResult() { _result.value = null }
    fun clearRecoveryCodes() { _recoveryCodes.value = emptyList() }

    fun login(identifier: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.login(identifier, password).map { "Signed in as ${it.username}." }
    }

    fun register(username: String, email: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.register(username, email, password, AltchaSolver()).map {
            "Account created. You can sign in now."
        }
    }

    fun claim(username: String, legacyPassword: String, email: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.claim(username, legacyPassword, email, password, AltchaSolver()).map {
            "Reserved nickname claimed. You can sign in now."
        }
    }

    fun forgot(identifier: String, done: (Boolean) -> Unit) = run(done) {
        repository.forgot(identifier, AltchaSolver()).map {
            "If the account exists, recovery instructions have been sent."
        }
    }

    fun recover(username: String, code: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.recoverWithCode(username, code, password).map { "Password changed. You can sign in now." }
    }

    fun reset(token: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.resetPassword(token, password).map { "Password changed. You can sign in now." }
    }

    fun logout() = run {
        repository.logout().map { "Signed out." }
    }

    fun refresh() = run { repository.refresh().map { "Account refreshed." } }

    fun loadDevices() = run {
        repository.devices().map { list -> _devices.value = list; "Devices refreshed." }
    }

    fun revokeSession(sessionId: String) = run {
        repository.revokeSession(sessionId).map {
            _devices.value = _devices.value.filterNot { device -> device.sessionId == sessionId }
            "Device signed out."
        }
    }

    fun revokeOthers() = run {
        repository.revokeOthers().map { count ->
            _devices.value = _devices.value.filter(AccountDevice::current)
            "$count other device(s) signed out."
        }
    }

    fun changePassword(current: String, replacement: String, done: (Boolean) -> Unit) = run(done) {
        repository.changePassword(current, replacement).map { "Password changed." }
    }

    fun changeEmail(email: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.changeEmail(email, password).map { "Check the new email address to confirm the change." }
    }

    fun confirmEmail(token: String, done: (Boolean) -> Unit) = run(done) {
        repository.confirmEmail(token).map { repository.refresh(); "Email address changed." }
    }

    fun regenerateCodes(password: String, done: (Boolean) -> Unit) = run(done) {
        repository.recoveryCodes(password).map { codes ->
            _recoveryCodes.value = codes
            "New recovery codes generated. Save them now."
        }
    }

    fun deleteAccount(password: String, confirmation: String, done: (Boolean) -> Unit) = run(done) {
        repository.deleteAccount(password, confirmation).map { "Account deleted." }
    }

    fun loadAdmin(page: Int = adminAccounts.value?.page ?: 1, showResult: Boolean = false) {
        viewModelScope.launch {
            val statsResult = repository.adminStats()
            val accountsResult = repository.adminAccounts(page)
            statsResult.onSuccess { _adminStats.value = it }
            accountsResult.onSuccess { _adminAccounts.value = it }
            val error = statsResult.exceptionOrNull() ?: accountsResult.exceptionOrNull()
            if (error != null || showResult) {
                _result.value = error?.message ?: "Account list refreshed."
            }
        }
    }

    fun adminRename(accountId: String, username: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.adminRename(accountId, username, password).map {
            loadAdmin(); "Account renamed to $it."
        }
    }

    fun adminModerator(accountId: String, enabled: Boolean, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.adminModerator(accountId, enabled, password).map {
            loadAdmin(); if (it) "Moderator access enabled." else "Moderator access removed."
        }
    }

    fun adminDelete(accountId: String, password: String, done: (Boolean) -> Unit) = run(done) {
        repository.adminDelete(accountId, password).map {
            loadAdmin(); "Account deleted."
        }
    }

    private fun run(done: ((Boolean) -> Unit)? = null, block: suspend () -> Result<String>) {
        viewModelScope.launch {
            val result = runCatching { block() }.getOrElse { Result.failure(it) }
            _result.value = result.fold({ it }, { it.message ?: "Account request failed." })
            done?.invoke(result.isSuccess)
        }
    }
}
