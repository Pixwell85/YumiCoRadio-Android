// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.media.RingtoneManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import net.yumicoradio.android.R
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.chat.NotificationMode
import net.yumicoradio.android.chat.model.NickState
import net.yumicoradio.android.ui.components.LocalChatFontScale
import net.yumicoradio.android.ui.components.LocalChatShowTimestamps
import net.yumicoradio.android.ui.components.MiniPlayer
import net.yumicoradio.android.ui.components.TabBar
import net.yumicoradio.android.ui.components.TabItem
import net.yumicoradio.android.ui.components.Win98Window
import net.yumicoradio.android.ui.theme.Win98

/** Enum, not a sealed interface: rememberSaveable's autoSaver needs a Serializable value. */
enum class Screen { Player, History, Schedule, Options, About, Chat, Contact }

@Composable
fun Shell(
    vm: PlayerViewModel,
    onShare: (String) -> Unit,
    onSleep: () -> Unit,
    onMinimize: () -> Unit,
    onQuit: () -> Unit,
    openChatSignal: Int = 0,
    openPlayerSignal: Int = 0,
) {
    var screen by rememberSaveable { mutableStateOf(Screen.Player) }

    // A chat notification was tapped: jump to the Chat tab. Keyed on the signal so a fresh tap while
    // the app is already open still switches, and so the initial value from a cold launch works too.
    LaunchedEffect(openChatSignal) {
        if (openChatSignal > 0) screen = Screen.Chat
    }

    // The media notification was tapped: land on the Player, whatever tab was last open.
    LaunchedEffect(openPlayerSignal) {
        if (openPlayerSignal > 0) screen = Screen.Player
    }

    // A PM ding while the app is on screen (any tab). repeatOnLifecycle(STARTED) means it does not
    // fire in the background — there, ChatConnectionService's own notification already sounds.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        val app = context.applicationContext as YumiApp
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            app.chat.pmSound.collect {
                // Respect "Nothing" the same way the notifications do: a user who silenced chat
                // should not be dinged either.
                if (app.prefs.notificationMode.first() == NotificationMode.NONE) return@collect
                runCatching {
                    RingtoneManager
                        .getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        ?.play()
                }
            }
        }
    }

    val back = { screen = Screen.Player }
    BackHandler(enabled = screen != Screen.Player) { back() }

    // The nav bar rides every screen, so sub-views reach each other directly. Back lives here
    // rather than in the title bar: a Win9x title bar has a fixed height and holds no controls.
    val tabs = buildList {
        if (screen != Screen.Player) add(TabItem("◀") { back() })
        add(TabItem("History") { screen = Screen.History })
        add(TabItem("Schedule") { screen = Screen.Schedule })
        add(TabItem("Options") { screen = Screen.Options })
        add(TabItem("Chat") { screen = Screen.Chat })
        add(TabItem("Contact") { screen = Screen.Contact })
        add(TabItem("About") { screen = Screen.About })
    }

    // The desktop behind the windows: the website's own body gradient, not a flat colour.
    Box(Modifier.fillMaxSize().background(Win98.DesktopBrush)) {
        when (screen) {
            Screen.Player -> PlayerFrame {
                NowPlayingScreen(
                    vm = vm, tabs = tabs, onShare = onShare, onSleep = onSleep,
                    onMinimize = onMinimize, onClose = onQuit,
                )
            }
            Screen.History ->
                SubView("Recently Played", R.drawable.ic_win_history, vm, tabs, back, onMinimize) { HistoryContent(vm) }
            Screen.Options -> {
                // The chat's settings live here too, so everything is in one place.
                val chatVm: ChatViewModel = viewModel()
                SubView("Options", R.drawable.ic_win_settings, vm, tabs, back, onMinimize) { SettingsContent(vm) }
            }
            Screen.Contact ->
                SubView("Contact", R.drawable.ic_win_contact, vm, tabs, back, onMinimize) {
                    ContactContent()
                }
            Screen.About ->
                SubView("About", R.drawable.ic_win_about, vm, tabs, back, onMinimize) { AboutContent() }
            Screen.Schedule -> {
                val scheduleVm: ScheduleViewModel = viewModel()
                SubView("Programming Schedule", R.drawable.ic_win_schedule, vm, tabs, back, onMinimize) {
                    ScheduleContent(vm, scheduleVm)
                }
            }
            Screen.Chat -> {
                val chatVm: ChatViewModel = viewModel()
                // Provided above the sub-view so it reaches the chat body and the PM dialog alike;
                // every piece of chat text reads it for its size.
                val fontSize by chatVm.chatFontSize.collectAsState()
                val showTimestamps by chatVm.showTimestamps.collectAsState()
                // Once joined, the title bar carries the nickname and presence, the way the website's
                // Live Chat window does. The bar ellipsises, so a long nick degrades gracefully.
                val nickState by chatVm.nick.collectAsState()
                val status by chatVm.status.collectAsState()
                val chatTitle = (nickState as? NickState.Joined)?.let {
                    "Live Chat — ${it.nickname} · ${status.label}"
                } ?: "Live Chat"
                CompositionLocalProvider(
                    LocalChatFontScale provides fontSize.scale,
                    LocalChatShowTimestamps provides showTimestamps,
                ) {
                    SubView(chatTitle, R.drawable.ic_win_chat, vm, tabs, back, onMinimize) { ChatContent(chatVm) }
                }
            }
        }
    }
}

/**
 * Centres the player window, and scrolls instead of clipping when it outgrows the screen.
 *
 * `heightIn(min = maxHeight)` is what makes both work: inside a scroll container the column is
 * measured with unbounded height, so `Arrangement.Center` would have nothing to centre within.
 * Forcing a minimum of one viewport gives it that space back.
 */
@Composable
private fun PlayerFrame(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Sub-view chrome: nav bar under the title, content filling the rest, mini player pinned below.
 * The window fills the frame, so nothing is parked against the status bar. Closing a sub-view
 * returns to the player — closing a window reveals what is behind it.
 */
@Composable
private fun SubView(
    title: String,
    @DrawableRes icon: Int,
    vm: PlayerViewModel,
    tabs: List<TabItem>,
    onBack: () -> Unit,
    onMinimize: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val np by vm.nowPlaying.collectAsState()
    val playing by vm.isPlaying.collectAsState()

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
        Win98Window(
            title = title,
            modifier = Modifier.fillMaxWidth().weight(1f),
            icon = icon,
            onMinimize = onMinimize,
            onClose = onBack,
            menuBar = { TabBar(tabs) },
        ) {
            content()
        }
        Spacer(Modifier.height(8.dp))
        MiniPlayer(
            np = np,
            playing = playing,
            onToggle = { vm.toggle() },
            onOpen = onBack,
        )
    }
}
