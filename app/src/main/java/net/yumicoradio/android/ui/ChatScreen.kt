package net.yumicoradio.android.ui

import android.content.Intent
import android.net.Uri as AndroidUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import net.yumicoradio.android.chat.ChatStatus
import net.yumicoradio.android.ui.components.StatusLed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.chat.ChatScroll
import net.yumicoradio.android.chat.MediaLinks
import net.yumicoradio.android.chat.ReservePassword
import net.yumicoradio.android.chat.UploadClient
import net.yumicoradio.android.chat.model.ConnectionState
import net.yumicoradio.android.chat.model.NickState
import net.yumicoradio.android.ui.components.*
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Type

@Composable
fun ColumnScope.ChatContent(vm: ChatViewModel) {
    val state by vm.state.collectAsState()
    val users by vm.users.collectAsState()
    val nickState by vm.nick.collectAsState()
    val connection by vm.connection.collectAsState()
    val notice by vm.notice.collectAsState()
    val colors by vm.colors.collectAsState()
    val storedNick by vm.storedNick.collectAsState()

    var draft by remember { mutableStateOf("") }
    // Lets the toolbar reopen the nickname dialog while already joined.
    var askNick by remember { mutableStateOf(false) }
    var showUsers by remember { mutableStateOf(false) }
    var showEmotes by remember { mutableStateOf(false) }
    var showQuota by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }

    val quota by vm.quota.collectAsState()
    val status by vm.status.collectAsState()
    val pm by vm.pm.collectAsState()
    val uploadsEnabled by vm.uploadsEnabled.collectAsState()
    val uploading by vm.uploading.collectAsState()
    val context = LocalContext.current

    // Which conversation the next pick belongs to: a nickname for a PM, null for the channel.
    // Held outside the launcher because the result arrives long after the button was pressed.
    var uploadTarget by remember { mutableStateOf<String?>(null) }

    // The system picker grants access to the one file chosen, so the app needs no storage
    // permission of its own.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        // A cancelled pick still has to release the hold taken when the picker was launched.
        if (uri != null) vm.upload(uri, uploadTarget) else vm.releaseTransferHold()
    }
    val openLink: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, AndroidUri.parse(url)))
        }
    }
    val listState = rememberLazyListState()
    val messages = state.buffer(state.active)

    // Whether the end of the list is on screen right now. A fact about the current layout.
    val atBottom by remember {
        derivedStateOf {
            ChatScroll.atBottom(
                lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                totalItems = listState.layoutInfo.totalItemsCount,
            )
        }
    }

    // Whether to keep following the conversation. Deliberately *not* the same thing as [atBottom]:
    // it is an intent, it sticks, and only a real scroll gesture changes it.
    //
    // Gating the auto-scroll on [atBottom] alone was the bug. Opening the keyboard shrinks the
    // viewport and pushes the last message out of view, which is indistinguishable from the reader
    // having scrolled up — so the chat stopped following exactly when someone started typing.
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            // Read the position once the list has settled; mid-fling it means nothing.
            if (!scrolling) following = atBottom
        }
    }

    // A new message, or a channel switch.
    LaunchedEffect(messages.size, state.active) {
        if (messages.isNotEmpty() && following) listState.animateScrollToItem(messages.lastIndex)
    }

    // The viewport itself resizing — the keyboard opening or closing, the emote picker appearing,
    // a rotation. No message arrives, so the effect above never fires, and before this the last
    // line simply stayed hidden behind whatever had just appeared.
    //
    // Instant rather than animated: this runs while the keyboard is still sliding, and an
    // animation racing that resize reads as lag.
    val viewportHeight by remember { derivedStateOf { listState.layoutInfo.viewportSize.height } }
    LaunchedEffect(viewportHeight) {
        if (messages.isNotEmpty() && following) listState.scrollToItem(messages.lastIndex)
    }

    ChatToolbar(
        canConnect = connection != ConnectionState.CONNECTED,
        canDisconnect = connection != ConnectionState.DISCONNECTED,
        usersShown = showUsers,
        userCount = users.size,
        status = status,
        onConnect = { if (storedNick.isBlank()) askNick = true else vm.join(storedNick) },
        onDisconnect = { vm.leave() },
        onNickname = { askNick = true },
        onToggleUsers = { showUsers = !showUsers },
        onStatus = { showStatusMenu = true },
        onQuota = { showQuota = true },
    )
    if (showStatusMenu) {
        StatusMenu(
            current = status,
            onPick = { vm.setStatus(it); showStatusMenu = false },
            onDismiss = { showStatusMenu = false },
        )
    }
    Spacer(Modifier.height(6.dp))

    if (showUsers) {
        UserListPanel(users = users, colors = colors, onPick = { vm.openPm(it) })
        Spacer(Modifier.height(6.dp))
    }

    ChannelBar(
        active = state.active,
        unread = state.unread,
        onPick = { vm.switchChannel(it) },
        pmThreads = pm.open.toList(),
        pmUnread = pm.unread,
        activePm = pm.active,
        onPickPm = { vm.openPm(it) },
    )
    Spacer(Modifier.height(6.dp))

    Column(
        Modifier.fillMaxWidth().weight(1f).background(Win98.Sunken).sunkenDeep().padding(3.dp),
    ) {
        if (messages.isEmpty()) {
            Text(
                if (connection == ConnectionState.CONNECTED) "Nothing said yet…" else "Connecting…",
                fontSize = 12.sp, fontFamily = W95FA, color = Win98.InkDim,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(messages) { msg ->
                    ChatLine(msg, colors)
                    MediaPreviews(links = MediaLinks.find(msg.text), onOpen = openLink)
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))

    Text(
        "${users.size} online · ${state.active.label}",
        fontSize = 10.sp, fontFamily = W95FA, color = Win98.Ink,
    )
    Spacer(Modifier.height(6.dp))

    if (showEmotes) {
        EmotePicker(onPick = { shortcut ->
            // Appended with a trailing space so shortcuts never run into each other or the next word.
            draft = if (draft.isEmpty() || draft.endsWith(" ")) "$draft$shortcut " else "$draft $shortcut "
        })
        Spacer(Modifier.height(6.dp))
    }

    ChatInput(
        value = draft,
        onValue = { draft = it },
        // Speaking rejoins the conversation: having your own message land off-screen because you
        // had scrolled up to read back is never what you meant.
        onSend = { vm.send(draft); draft = ""; following = true },
        enabled = nickState is NickState.Joined,
        emotesShown = showEmotes,
        onToggleEmotes = { showEmotes = !showEmotes },
        uploadsEnabled = uploadsEnabled && nickState is NickState.Joined && !uploading,
        onUpload = { uploadTarget = null; vm.holdForTransfer(); pickFile.launch("*/*") },
    )

    // Joining on arrival is what makes the Chat tab feel like opening a chat. It only asks for a
    // nickname when there is none to use — pressing Disconnect must never summon this dialog.
    LaunchedEffect(Unit) {
        if (connection == ConnectionState.DISCONNECTED && nickState is NickState.Idle) {
            if (storedNick.isNotBlank()) vm.join(storedNick) else askNick = true
        }
    }

    // Dialogs. The nickname is remembered between launches; the password never is.
    when (val ns = nickState) {
        is NickState.NeedsNick ->
            NickDialog(
                initial = storedNick,
                onCancel = { askNick = false; vm.cancelNickPrompt() },
                onJoin = { askNick = false; vm.join(it) },
            )
        is NickState.NeedsPassword ->
            PasswordDialog(
                nickname = ns.nickname,
                onSubmit = { vm.submitPassword(ns.nickname, it) },
                onCancel = { vm.leave() },
            )
        is NickState.SettingPassword ->
            SetPasswordDialog(
                slot = ns.slot,
                error = ns.error,
                onSubmit = { vm.submitReservePassword(it) },
                onCancel = { vm.cancelReservePassword(ns.previousNick) },
            )
        is NickState.Rejected ->
            NickDialog(
                initial = ns.nickname,
                error = when (ns.reason) {
                    "taken" -> "That nickname is already in use."
                    "invalid" -> "That nickname is not allowed."
                    else -> "The server refused that nickname."
                },
                onCancel = { askNick = false; vm.cancelNickPrompt() },
                onJoin = { vm.join(it) },
            )
        else -> Unit
    }

    // Asked for from the toolbar, or by a connect attempt with no stored nickname. Always
    // cancellable — joining again under a new nickname is what the server's `join` does, so there
    // is nothing to undo if the user changes their mind.
    if (askNick && nickState !is NickState.NeedsNick) {
        NickDialog(
            initial = storedNick,
            onJoin = { askNick = false; vm.join(it) },
            onCancel = { askNick = false },
        )
    }

    if (uploading) {
        val progress by vm.uploadProgress.collectAsState()
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    progress?.let {
                        "Uploading ${(it.fraction * 100).toInt()}% · " +
                            "${UploadClient.formatSize(it.sent)} / ${UploadClient.formatSize(it.total)}"
                    } ?: "Uploading…",
                    fontSize = 10.sp, fontFamily = W95FA, color = Win98.Ink,
                )
                progress?.let {
                    Text(
                        UploadClient.formatSpeed(it.bytesPerSecond),
                        fontSize = 10.sp, fontFamily = W95FA, color = Win98.InkDim,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Win98ProgressBar(fraction = progress?.fraction ?: 0f)
        }
    }

    if (showQuota) {
        Win98Dialog(
            title = "Upload quota",
            onDismiss = { showQuota = false },
            buttons = { Win98Button("OK") { showQuota = false } },
        ) {
            DialogText(quota.summary)
            Spacer(Modifier.height(4.dp))
            DialogText("${quota.format(quota.remaining)} left")
            quota.resetLabel()?.let { reset ->
                Spacer(Modifier.height(4.dp))
                DialogText("Resets $reset")
            }
            if (!uploadsEnabled) {
                Spacer(Modifier.height(8.dp))
                DialogText("Uploads are currently disabled on the server.")
            }
        }
    }

    pm.active?.let { nick ->
        PmWindow(
            nickname = nick,
            messages = pm.messages(nick),
            colors = colors,
            canSend = nickState is NickState.Joined,
            onSend = { vm.sendPm(nick, it) },
            onMinimise = { vm.closePm() },
            onClose = { vm.dismissPm(nick) },
            onOpenLink = openLink,
            uploadsEnabled = uploadsEnabled && nickState is NickState.Joined && !uploading,
            onUpload = { uploadTarget = nick; vm.holdForTransfer(); pickFile.launch("*/*") },
        )
    }

    notice?.let { text ->
        Win98Dialog(
            title = "Chat",
            onDismiss = { vm.clearNotice() },
            buttons = { Win98Button("OK") { vm.clearNotice() } },
        ) {
            DialogText(text)
        }
    }
}

/**
 * Body text inside a pop-up, at the chrome size the rest of the app uses.
 *
 * The dialogs each carried their own 12sp and 13sp before this — the scattered sizes the design
 * system exists to stop.
 */
@Composable
private fun DialogText(text: String, color: Color = Win98.Ink) {
    Text(
        text,
        fontFamily = W95FA,
        fontSize = Win98Type.Body,
        lineHeight = Win98Type.BodyLineHeight,
        color = color,
    )
}

@Composable
private fun NickDialog(
    initial: String,
    error: String? = null,
    onCancel: (() -> Unit)? = null,
    onJoin: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    // Dismissability rides on `onCancel`: every caller passes one, so the X, back and an outside
    // tap all cancel. While a modal is up the whole app is blocked, so a prompt with no way out is
    // a trap — cancelling simply leaves the chat unjoined.
    Win98Dialog(
        title = "Choose a nickname",
        onDismiss = onCancel,
        buttons = {
            onCancel?.let { Win98Button("Cancel", onClick = it) }
            Win98Button("Join", enabled = value.isNotBlank()) {
                if (value.isNotBlank()) onJoin(value.trim())
            }
        },
    ) {
        if (error != null) {
            DialogText(error, color = Win98.Error)
            Spacer(Modifier.height(8.dp))
        }
        DialogField(
            value = value,
            onValue = { value = it },
            mask = false,
            onSubmit = { if (value.isNotBlank()) onJoin(value.trim()) },
        )
    }
}

@Composable
private fun PasswordDialog(nickname: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf("") }
    Win98Dialog(
        title = "\"$nickname\" is reserved",
        onDismiss = onCancel,
        buttons = {
            Win98Button("Cancel", onClick = onCancel)
            Win98Button("Join", enabled = value.isNotBlank()) {
                if (value.isNotBlank()) onSubmit(value)
            }
        },
    ) {
        DialogText("Enter its password. It is not stored — you will be asked again next launch.")
        Spacer(Modifier.height(8.dp))
        DialogField(
            value = value,
            onValue = { value = it },
            mask = true,
            onSubmit = { if (value.isNotBlank()) onSubmit(value) },
        )
    }
}

/**
 * Choosing a password for a nickname an admin is reserving. Two fields with a confirmation, unlike
 * [PasswordDialog] which enters a known one. Validated locally before sending; the slot name is
 * shown because it may differ from the nickname the user is currently on (a password reset comes in
 * under a temporary name).
 */
@Composable
private fun SetPasswordDialog(
    slot: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    fun attempt() {
        when (ReservePassword.validate(password, confirm)) {
            ReservePassword.Error.TOO_SHORT ->
                localError = "At least ${ReservePassword.MIN_LENGTH} characters."
            ReservePassword.Error.TOO_LONG ->
                localError = "At most ${ReservePassword.MAX_LENGTH} characters."
            ReservePassword.Error.MISMATCH ->
                localError = "The two passwords do not match."
            null -> { localError = null; onSubmit(password) }
        }
    }

    Win98Dialog(
        title = "\"$slot\" is being reserved for you",
        onDismiss = onCancel,
        buttons = {
            Win98Button("Cancel", onClick = onCancel)
            Win98Button("OK", enabled = password.isNotBlank() && confirm.isNotBlank()) { attempt() }
        },
    ) {
        DialogText("Choose a password. You will be asked for it each time you connect as \"$slot\".")
        Spacer(Modifier.height(8.dp))
        DialogField(value = password, onValue = { password = it }, mask = true, onSubmit = ::attempt)
        Spacer(Modifier.height(6.dp))
        DialogField(value = confirm, onValue = { confirm = it }, mask = true, onSubmit = ::attempt)
        // The server's rejection (error) or the local one; the local check catches all but a race.
        (localError ?: error)?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = Win98.Error, fontFamily = W95FA, fontSize = 12.sp)
        }
    }
}

/**
 * [onSubmit] is what the keyboard's own action key does. Without it the key just closed the
 * keyboard, leaving the reader to hunt for "Join" — the same defect the chat composer had, and one
 * that shows up on every launch because a reserved nickname is asked for its password each time.
 *
 * It carries the same blank check as the button, so the key cannot submit what the button refuses.
 */
@Composable
private fun DialogField(
    value: String,
    onValue: (String) -> Unit,
    mask: Boolean,
    onSubmit: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().background(Win98.Sunken).sunken()
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                // A password field must not be autocorrected or suggested into the IME's history.
                keyboardType = if (mask) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = !mask,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            visualTransformation =
                if (mask) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = TextStyle(color = Win98.Ink, fontFamily = W95FA, fontSize = 13.sp),
            cursorBrush = SolidColor(Win98.Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The three-way presence chooser, as the site's status context menu: a LED and label per state,
 * the current one marked. A plain dialog rather than a floating menu — it is opened rarely and this
 * keeps it dismissable the same way every other dialog here is.
 */
@Composable
private fun StatusMenu(
    current: ChatStatus,
    onPick: (ChatStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    Win98Dialog(
        title = "Status",
        onDismiss = onDismiss,
        buttons = { Win98Button("Cancel", onClick = onDismiss) },
    ) {
        ChatStatus.entries.forEach { s ->
            Row(
                Modifier.fillMaxWidth()
                    .tappable { onPick(s) }
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLed(s, size = 10.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    s.label,
                    fontFamily = W95FA, fontSize = Win98Type.Body, color = Win98.Ink,
                    fontWeight = if (s == current) FontWeight.Bold else FontWeight.Normal,
                )
                if (s == current) {
                    Spacer(Modifier.weight(1f))
                    Text("•", fontFamily = W95FA, fontSize = Win98Type.Body, color = Win98.Ink)
                }
            }
        }
    }
}
