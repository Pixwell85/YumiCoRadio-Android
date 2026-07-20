package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.yumicoradio.android.chat.ChatScroll
import net.yumicoradio.android.chat.MediaLinks
import net.yumicoradio.android.chat.model.ChatMessage
import net.yumicoradio.android.R
import net.yumicoradio.android.ui.components.*
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

/**
 * A private conversation, in its own window over the main one.
 *
 * The title bar means what it says on a real window: `_` [onMinimise] tucks the thread away, still
 * reachable from its button beside the channels, while `✕` [onClose] closes the conversation for
 * good and takes that button with it.
 *
 * `usePlatformDefaultWidth = false` because a Win9x window sizes itself, and Material's default
 * dialog width would pin it to something narrower than the chat it mirrors.
 */
@Composable
fun PmWindow(
    nickname: String,
    messages: List<ChatMessage>,
    colors: Map<String, String>,
    canSend: Boolean,
    onSend: (String) -> Unit,
    onMinimise: () -> Unit,
    onClose: () -> Unit,
    onOpenLink: (String) -> Unit,
    uploadsEnabled: Boolean,
    onUpload: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var showEmotes by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // The same follow rule as the main chat — see ChatContent for why the intent has to be kept
    // apart from the layout fact. A PM window is where the keyboard is up most of the time, so
    // this is the window the bug hurt most.
    val atBottom by remember {
        derivedStateOf {
            ChatScroll.atBottom(
                lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
                totalItems = listState.layoutInfo.totalItemsCount,
            )
        }
    }
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) following = atBottom
        }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && following) listState.animateScrollToItem(messages.lastIndex)
    }
    val viewportHeight by remember { derivedStateOf { listState.layoutInfo.viewportSize.height } }
    LaunchedEffect(viewportHeight) {
        if (messages.isNotEmpty() && following) listState.scrollToItem(messages.lastIndex)
    }

    Dialog(
        // Back and a tap outside minimise rather than close: losing a conversation should take a
        // deliberate press on ✕, not a stray gesture.
        onDismissRequest = onMinimise,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Its own window, so it needs its own soft-input mode; without this the window never
        // shrinks for the keyboard and the conversation stays buried behind it.
        AdjustResizeForKeyboard()
        Box(Modifier.fillMaxSize().imePadding().padding(12.dp), contentAlignment = Alignment.Center) {
            Win98Window(
                title = "Private — $nickname",
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                icon = R.drawable.ic_win_contact,
                onMinimize = onMinimise,
                onClose = onClose,
            ) {
                Column(
                    Modifier.fillMaxWidth().weight(1f).background(Win98.Sunken).sunkenDeep()
                        .padding(3.dp),
                ) {
                    if (messages.isEmpty()) {
                        Text(
                            "Say something to $nickname…",
                            fontSize = 12.sp, fontFamily = W95FA, color = Win98.InkDim,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), state = listState) {
                            items(messages) { msg ->
                                ChatLine(msg, colors)
                                MediaPreviews(
                                    links = MediaLinks.find(msg.text),
                                    onOpen = onOpenLink,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (showEmotes) {
                    EmotePicker(onPick = { shortcut ->
                        draft =
                            if (draft.isEmpty() || draft.endsWith(" ")) "$draft$shortcut "
                            else "$draft $shortcut "
                    })
                    Spacer(Modifier.height(6.dp))
                }

                ChatInput(
                    value = draft,
                    onValue = { draft = it },
                    onSend = { onSend(draft); draft = ""; following = true },
                    enabled = canSend,
                    emotesShown = showEmotes,
                    onToggleEmotes = { showEmotes = !showEmotes },
                    // Same upload endpoint as the channel; only the message announcing the URL
                    // differs, going out as a private-message to this conversation.
                    uploadsEnabled = uploadsEnabled,
                    onUpload = onUpload,
                )
            }
        }
    }
}
