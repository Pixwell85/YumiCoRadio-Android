// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.R
import net.yumicoradio.android.account.AccountDevice
import net.yumicoradio.android.account.AccountProfile
import net.yumicoradio.android.account.AdminAccount
import net.yumicoradio.android.account.AdminAccountPage
import net.yumicoradio.android.account.AdminStats
import net.yumicoradio.android.account.canManageAccounts
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.components.Win98Fieldset
import net.yumicoradio.android.ui.components.Win98ProgressBar
import net.yumicoradio.android.ui.components.sunken
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import java.text.DateFormat
import java.util.Date

private enum class AccountAction {
    LOGIN, REGISTER, CLAIM, FORGOT, RECOVERY_CODE, RESET_TOKEN,
    CHANGE_PASSWORD, CHANGE_EMAIL, CONFIRM_EMAIL, RECOVERY_CODES, DELETE_ACCOUNT,
}

private enum class AdminActionType { RENAME, MODERATOR, DELETE }
private data class AdminAction(val type: AdminActionType, val account: AdminAccount)

@Composable
fun ColumnScope.AccountContent(vm: AccountViewModel, onOpenMyVotes: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    val devices by vm.devices.collectAsState()
    val codes by vm.recoveryCodes.collectAsState()
    val adminStats by vm.adminStats.collectAsState()
    val adminAccounts by vm.adminAccounts.collectAsState()
    val result by vm.result.collectAsState()
    var action by remember { mutableStateOf<AccountAction?>(null) }
    var adminAction by remember { mutableStateOf<AdminAction?>(null) }

    LaunchedEffect(state.profile?.role) {
        if (canManageAccounts(state.profile?.role)) vm.loadAdmin()
    }

    Column(
        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(end = 2.dp),
    ) {
        when {
            state.restoring -> WorkingPanel("Restoring account session...")
            state.signedIn -> SignedInAccount(
                profile = state.profile,
                devices = devices,
                offline = state.offline,
                onRefresh = vm::refresh,
                onDevices = vm::loadDevices,
                onRevoke = vm::revokeSession,
                onRevokeOthers = vm::revokeOthers,
                onAction = { action = it },
                onOpenMyVotes = onOpenMyVotes,
                onLogout = vm::logout,
                adminStats = adminStats,
                adminAccounts = adminAccounts,
                onLoadAdmin = vm::loadAdmin,
                onAdminAction = { adminAction = it },
            )
            else -> SignedOutAccount(onAction = { action = it })
        }
    }

    action?.let { selected ->
        AccountFormDialog(
            action = selected,
            username = state.username,
            busy = state.busy,
            onDismiss = { if (!state.busy) action = null },
            onSubmit = { values ->
                val done: (Boolean) -> Unit = { success -> if (success) action = null }
                when (selected) {
                    AccountAction.LOGIN -> vm.login(values.identifier, values.password, done)
                    AccountAction.REGISTER -> vm.register(values.username, values.email, values.password, done)
                    AccountAction.CLAIM -> vm.claim(values.username, values.legacyPassword, values.email, values.password, done)
                    AccountAction.FORGOT -> vm.forgot(values.identifier, done)
                    AccountAction.RECOVERY_CODE -> vm.recover(values.username, values.code, values.password, done)
                    AccountAction.RESET_TOKEN -> vm.reset(values.token, values.password, done)
                    AccountAction.CHANGE_PASSWORD -> vm.changePassword(values.currentPassword, values.password, done)
                    AccountAction.CHANGE_EMAIL -> vm.changeEmail(values.email, values.password, done)
                    AccountAction.CONFIRM_EMAIL -> vm.confirmEmail(values.token, done)
                    AccountAction.RECOVERY_CODES -> vm.regenerateCodes(values.password, done)
                    AccountAction.DELETE_ACCOUNT -> vm.deleteAccount(values.password, values.confirmation, done)
                }
            },
        )
    }


    adminAction?.let { selected ->
        AdminActionDialog(
            action = selected,
            busy = state.busy,
            onDismiss = { if (!state.busy) adminAction = null },
            onSubmit = { username, password ->
                val done: (Boolean) -> Unit = { success -> if (success) adminAction = null }
                when (selected.type) {
                    AdminActionType.RENAME -> vm.adminRename(selected.account.accountId, username, password, done)
                    AdminActionType.MODERATOR -> vm.adminModerator(
                        selected.account.accountId, !selected.account.moderator, password, done,
                    )
                    AdminActionType.DELETE -> vm.adminDelete(selected.account.accountId, password, done)
                }
            },
        )
    }

    if (codes.isNotEmpty()) {
        RecoveryCodesDialog(codes, onDismiss = vm::clearRecoveryCodes)
    }
    result?.let { message ->
        Win98Dialog(
            title = "Account",
            icon = R.drawable.ic_win_account,
            onDismiss = vm::clearResult,
            buttons = { Win98Button("OK", onClick = vm::clearResult) },
        ) { AccountText(message) }
    }
}

@Composable
private fun SignedOutAccount(onAction: (AccountAction) -> Unit) {
    Win98Fieldset("Yumi Co. Radio Account") {
        AccountText("Sign in to use your reserved nickname everywhere and synchronize My Votes.")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Win98Button("Log in", modifier = Modifier.weight(1f)) { onAction(AccountAction.LOGIN) }
            Win98Button("Create account", modifier = Modifier.weight(1f)) { onAction(AccountAction.REGISTER) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Win98Fieldset("Existing Live Chat nickname") {
        AccountText("Already have a reserved nickname? Claim it without changing its name.")
        Spacer(Modifier.height(8.dp))
        Win98Button("Claim reserved nickname", modifier = Modifier.fillMaxWidth()) { onAction(AccountAction.CLAIM) }
    }
    Spacer(Modifier.height(10.dp))
    Win98Fieldset("Account recovery") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Win98Button("Forgot password", modifier = Modifier.weight(1f)) { onAction(AccountAction.FORGOT) }
            Win98Button("Recovery code", modifier = Modifier.weight(1f)) { onAction(AccountAction.RECOVERY_CODE) }
        }
        Spacer(Modifier.height(8.dp))
        Win98Button("Use reset link token", modifier = Modifier.fillMaxWidth()) { onAction(AccountAction.RESET_TOKEN) }
    }
}

@Composable
private fun SignedInAccount(
    profile: AccountProfile?,
    devices: List<AccountDevice>,
    offline: Boolean,
    onRefresh: () -> Unit,
    onDevices: () -> Unit,
    onRevoke: (String) -> Unit,
    onRevokeOthers: () -> Unit,
    onAction: (AccountAction) -> Unit,
    onOpenMyVotes: () -> Unit,
    onLogout: () -> Unit,
    adminStats: AdminStats?,
    adminAccounts: AdminAccountPage?,
    onLoadAdmin: (Int, Boolean) -> Unit,
    onAdminAction: (AdminAction) -> Unit,
) {
    Win98Fieldset("Profile") {
        if (offline) AccountText("Offline - saved session will be checked again later.", error = true)
        ProfileLine("Nickname", profile?.username ?: "...")
        ProfileLine("E-mail", profile?.email ?: "...")
        ProfileLine("Member since", profile?.createdAtMs?.let(::date) ?: "...")
        ProfileLine("Role", when {
            profile?.role == "admin" -> "Administrator"
            profile?.moderator == true -> "Moderator"
            else -> "Member"
        })
        ProfileLine("Recovery codes", profile?.recoveryCodesRemaining?.toString() ?: "...")
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Win98Button("Refresh", modifier = Modifier.weight(1f), onClick = onRefresh)
            Win98Button("Open My Votes", modifier = Modifier.weight(1f), onClick = onOpenMyVotes)
        }
    }
    Spacer(Modifier.height(10.dp))
    Win98Fieldset("Security") {
        AccountButtonGrid(
            "Change password" to { onAction(AccountAction.CHANGE_PASSWORD) },
            "Change e-mail" to { onAction(AccountAction.CHANGE_EMAIL) },
            "Confirm e-mail" to { onAction(AccountAction.CONFIRM_EMAIL) },
            "Recovery codes" to { onAction(AccountAction.RECOVERY_CODES) },
        )
    }
    Spacer(Modifier.height(10.dp))
    Win98Fieldset("Active devices") {
        if (devices.isEmpty()) {
            AccountText("Load the current account sessions to manage connected devices.")
            Spacer(Modifier.height(8.dp))
            Win98Button("Load devices", modifier = Modifier.fillMaxWidth(), onClick = onDevices)
        } else {
            devices.forEach { device ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        AccountText(device.deviceLabel + if (device.current) " (this device)" else "")
                        AccountText("Last used ${date(device.lastUsedAtMs)}", dim = true)
                    }
                    if (!device.current) Win98Button("Sign out") { onRevoke(device.sessionId) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Win98Button("Sign out other devices", modifier = Modifier.fillMaxWidth(), onClick = onRevokeOthers)
        }
    }
    Spacer(Modifier.height(10.dp))
    Win98Fieldset("Session") {
        Win98Button("Log out", modifier = Modifier.fillMaxWidth(), onClick = onLogout)
    }
    if (canManageAccounts(profile?.role)) {
        Spacer(Modifier.height(10.dp))
        AccountAdminPanel(adminStats, adminAccounts, onLoadAdmin, onAdminAction)
    }
    Spacer(Modifier.height(10.dp))
    Win98Fieldset("Delete account") {
        AccountText("Deletion is permanent. Your nickname becomes available again.", error = true)
        Spacer(Modifier.height(8.dp))
        Win98Button("Delete account", modifier = Modifier.fillMaxWidth()) { onAction(AccountAction.DELETE_ACCOUNT) }
    }
}

@Composable
private fun AccountAdminPanel(
    stats: AdminStats?,
    accounts: AdminAccountPage?,
    onLoad: (Int, Boolean) -> Unit,
    onAction: (AdminAction) -> Unit,
) {
    Win98Fieldset("Account administration") {
        if (stats == null || accounts == null) {
            AccountText("Loading account administration...")
            Spacer(Modifier.height(8.dp))
            Win98Button("Retry", modifier = Modifier.fillMaxWidth()) { onLoad(1, true) }
            return@Win98Fieldset
        }
        AccountText(
            "Total ${stats.total} - Active ${stats.active} - Pending ${stats.pending} - " +
                "Locked ${stats.locked} - Moderators ${stats.moderators}",
        )
        Spacer(Modifier.height(8.dp))
        accounts.rows.forEach { account ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                AccountText(account.username + when {
                    account.role == "admin" -> " (administrator)"
                    account.moderator -> " (moderator)"
                    else -> ""
                })
                AccountText("${account.status} - ${date(account.createdAtMs)}", dim = true)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Win98Button("Rename", Modifier.weight(1f)) {
                        onAction(AdminAction(AdminActionType.RENAME, account))
                    }
                    Win98Button(if (account.moderator) "Unmod" else "Moderator", Modifier.weight(1f)) {
                        onAction(AdminAction(AdminActionType.MODERATOR, account))
                    }
                    Win98Button("Delete", Modifier.weight(1f)) {
                        onAction(AdminAction(AdminActionType.DELETE, account))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Win98Button("Previous", Modifier.weight(1f), enabled = accounts.page > 1) {
                onLoad(accounts.page - 1, false)
            }
            AccountText("Page ${accounts.page}/${accounts.totalPages.coerceAtLeast(1)}")
            Win98Button("Next", Modifier.weight(1f), enabled = accounts.page < accounts.totalPages) {
                onLoad(accounts.page + 1, false)
            }
        }
    }
}

@Composable
private fun AdminActionDialog(
    action: AdminAction,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var username by remember(action) { mutableStateOf(action.account.username) }
    var password by remember(action) { mutableStateOf("") }
    val title = when (action.type) {
        AdminActionType.RENAME -> "Rename account"
        AdminActionType.MODERATOR -> if (action.account.moderator) "Remove moderator" else "Make moderator"
        AdminActionType.DELETE -> "Delete account"
    }
    Win98Dialog(
        title = title,
        icon = R.drawable.ic_win_account,
        onDismiss = if (busy) null else onDismiss,
        buttons = {
            Win98Button("Cancel", enabled = !busy, onClick = onDismiss)
            Win98Button(if (action.type == AdminActionType.DELETE) "Delete" else "OK", enabled = !busy) {
                onSubmit(username, password)
            }
        },
    ) {
        AccountText("Account: ${action.account.username}")
        Spacer(Modifier.height(7.dp))
        if (action.type == AdminActionType.RENAME) {
            AccountField("New nickname", username, { username = it })
        }
        if (action.type == AdminActionType.DELETE) {
            AccountText("This permanently deletes the account and releases its nickname.", error = true)
            Spacer(Modifier.height(7.dp))
        }
        AccountField("Your administrator password", password, { password = it }, secret = true)
        if (busy) WorkingPanel("Working...")
    }
}

@Composable
private fun AccountButtonGrid(vararg actions: Pair<String, () -> Unit>) {
    actions.toList().chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (label, action) -> Win98Button(label, Modifier.weight(1f), onClick = action) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
    }
}

private data class AccountValues(
    val identifier: String = "",
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val currentPassword: String = "",
    val legacyPassword: String = "",
    val code: String = "",
    val token: String = "",
    val confirmation: String = "",
)

@Composable
private fun AccountFormDialog(
    action: AccountAction,
    username: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (AccountValues) -> Unit,
) {
    var identifier by remember(action) { mutableStateOf("") }
    var chosenUsername by remember(action) { mutableStateOf(username.orEmpty()) }
    var email by remember(action) { mutableStateOf("") }
    var password by remember(action) { mutableStateOf("") }
    var currentPassword by remember(action) { mutableStateOf("") }
    var legacyPassword by remember(action) { mutableStateOf("") }
    var confirm by remember(action) { mutableStateOf("") }
    var code by remember(action) { mutableStateOf("") }
    var token by remember(action) { mutableStateOf("") }
    var confirmation by remember(action) { mutableStateOf("") }
    var localError by remember(action) { mutableStateOf<String?>(null) }

    val needsNewPassword = action in setOf(
        AccountAction.REGISTER, AccountAction.CLAIM, AccountAction.RECOVERY_CODE,
        AccountAction.RESET_TOKEN, AccountAction.CHANGE_PASSWORD,
    )
    fun submit() {
        localError = when {
            needsNewPassword && password.length < 12 -> "Password must contain at least 12 characters."
            needsNewPassword && password != confirm -> "The two passwords do not match."
            else -> null
        }
        if (localError != null) return
        onSubmit(AccountValues(identifier, chosenUsername, email, password, currentPassword,
            legacyPassword, code, token, confirmation))
    }

    val title = when (action) {
        AccountAction.LOGIN -> "Log in"
        AccountAction.REGISTER -> "Create account"
        AccountAction.CLAIM -> "Claim reserved nickname"
        AccountAction.FORGOT -> "Forgot password"
        AccountAction.RECOVERY_CODE -> "Use recovery code"
        AccountAction.RESET_TOKEN -> "Reset password"
        AccountAction.CHANGE_PASSWORD -> "Change password"
        AccountAction.CHANGE_EMAIL -> "Change e-mail"
        AccountAction.CONFIRM_EMAIL -> "Confirm e-mail"
        AccountAction.RECOVERY_CODES -> "Generate recovery codes"
        AccountAction.DELETE_ACCOUNT -> "Delete account"
    }
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val contentMax = (screenHeight - 150.dp).coerceAtLeast(180.dp)
    Win98Dialog(
        title = title,
        icon = R.drawable.ic_win_account,
        onDismiss = if (busy) null else onDismiss,
        buttons = {
            Win98Button("Cancel", enabled = !busy, onClick = onDismiss)
            Win98Button(if (action == AccountAction.DELETE_ACCOUNT) "Delete" else "OK", enabled = !busy, onClick = ::submit)
        },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = contentMax).verticalScroll(rememberScrollState())) {
            when (action) {
                AccountAction.LOGIN -> {
                    AccountField("Username or e-mail", identifier, { identifier = it })
                    AccountField("Password", password, { password = it }, secret = true)
                }
                AccountAction.REGISTER -> {
                    AccountField("Nickname (cannot be changed)", chosenUsername, { chosenUsername = it })
                    AccountField("E-mail", email, { email = it }, email = true)
                    AccountField("Password", password, { password = it }, secret = true)
                    AccountField("Confirm password", confirm, { confirm = it }, secret = true)
                    AccountText("The anti-robot verification is solved locally before registration.", dim = true)
                }
                AccountAction.CLAIM -> {
                    AccountField("Reserved nickname", chosenUsername, { chosenUsername = it })
                    AccountField("Current Live Chat password", legacyPassword, { legacyPassword = it }, secret = true)
                    AccountField("E-mail", email, { email = it }, email = true)
                    AccountField("New account password", password, { password = it }, secret = true)
                    AccountField("Confirm password", confirm, { confirm = it }, secret = true)
                }
                AccountAction.FORGOT -> AccountField("Username or e-mail", identifier, { identifier = it })
                AccountAction.RECOVERY_CODE -> {
                    AccountField("Nickname", chosenUsername, { chosenUsername = it })
                    AccountField("Recovery code", code, { code = it })
                    AccountField("New password", password, { password = it }, secret = true)
                    AccountField("Confirm password", confirm, { confirm = it }, secret = true)
                }
                AccountAction.RESET_TOKEN -> {
                    AccountField("Reset token", token, { token = it })
                    AccountField("New password", password, { password = it }, secret = true)
                    AccountField("Confirm password", confirm, { confirm = it }, secret = true)
                }
                AccountAction.CHANGE_PASSWORD -> {
                    AccountField("Current password", currentPassword, { currentPassword = it }, secret = true)
                    AccountField("New password", password, { password = it }, secret = true)
                    AccountField("Confirm password", confirm, { confirm = it }, secret = true)
                }
                AccountAction.CHANGE_EMAIL -> {
                    AccountField("New e-mail", email, { email = it }, email = true)
                    AccountField("Password", password, { password = it }, secret = true)
                }
                AccountAction.CONFIRM_EMAIL -> AccountField("Confirmation token", token, { token = it })
                AccountAction.RECOVERY_CODES -> {
                    AccountText("Existing unused recovery codes will be replaced.")
                    AccountField("Password", password, { password = it }, secret = true)
                }
                AccountAction.DELETE_ACCOUNT -> {
                    AccountText("This cannot be undone.", error = true)
                    AccountField("Password", password, { password = it }, secret = true)
                    AccountField("Type ${username.orEmpty()}", confirmation, { confirmation = it })
                }
            }
            localError?.let { AccountText(it, error = true) }
            if (busy) WorkingPanel(if (action in setOf(AccountAction.REGISTER, AccountAction.CLAIM, AccountAction.FORGOT))
                "Verification in progress..." else "Working...")
        }
    }
}

@Composable
private fun AccountField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    secret: Boolean = false,
    email: Boolean = false,
) {
    AccountText(label)
    Box(
        Modifier.fillMaxWidth().background(Win98.Sunken).sunken()
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = when { secret -> KeyboardType.Password; email -> KeyboardType.Email; else -> KeyboardType.Text },
                autoCorrectEnabled = !secret && !email,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = Win98.Ink, fontFamily = W95FA, fontSize = 13.sp),
            cursorBrush = SolidColor(Win98.Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun WorkingPanel(text: String) {
    Spacer(Modifier.height(6.dp))
    AccountText(text, dim = true)
    Spacer(Modifier.height(4.dp))
    Win98ProgressBar(0.65f)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun RecoveryCodesDialog(codes: List<String>, onDismiss: () -> Unit) {
    Win98Dialog(
        title = "Recovery codes",
        icon = R.drawable.ic_win_account,
        onDismiss = onDismiss,
        buttons = { Win98Button("I saved them", onClick = onDismiss) },
    ) {
        AccountText("Each code works once. Save them somewhere safe; they will not be shown again.")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().background(Win98.Sunken).sunken().padding(10.dp)) {
            codes.forEach { AccountText(it) }
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink,
            modifier = Modifier.width(118.dp))
        Text(value, fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink,
            modifier = Modifier.weight(1f))
    }
}

@Composable
private fun AccountText(text: String, dim: Boolean = false, error: Boolean = false) {
    Text(
        text,
        fontFamily = W95FA,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = when { error -> Win98.Error; dim -> Win98.InkDim; else -> Win98.Ink },
    )
}

private fun date(timestamp: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
