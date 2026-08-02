// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import net.yumicoradio.android.chat.ChatStatus
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.compose.AsyncImage
import net.yumicoradio.android.R
import androidx.compose.ui.layout.ContentScale
import net.yumicoradio.android.chat.ChatAutocomplete
import net.yumicoradio.android.chat.EmoteParser
import net.yumicoradio.android.chat.MediaLinks
import net.yumicoradio.android.chat.Emotes
import net.yumicoradio.android.chat.MentionParser
import net.yumicoradio.android.chat.NickColors
import net.yumicoradio.android.chat.AudioTags
import net.yumicoradio.android.chat.UploadClient
import net.yumicoradio.android.chat.UserRoster
import net.yumicoradio.android.chat.model.ChatChannel
import net.yumicoradio.android.chat.model.ChatMessage
import net.yumicoradio.android.chat.model.ChatUser
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

private val UnreadDot = Color(0xFFFF8800)

/**
 * Multiplies every chat text size. Fed from the user's Text-size preference and read wherever chat
 * text is drawn, so one setting scales messages, the user list and the composer together.
 */
val LocalChatFontScale = androidx.compose.runtime.compositionLocalOf { 1f }

/** Whether to draw the `[HH:mm]` stamp before each line. Propagates into the PM dialog too. */
val LocalChatShowTimestamps = androidx.compose.runtime.compositionLocalOf { true }

private val chatTimeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT)

// The user-list role badge colours, taken straight from the website (`js/yumiChat-v2.js`).
private val AdminRed = Color(0xFFFF0000)
private val BotGreen = Color(0xFF2E8B57)
private val VoiceBlue = Color(0xFF0000CC)

/** Server-voiced types that render as a notice rather than as somebody speaking. */
private val NOTICE_TYPES = setOf("info", "error", "poll")

/**
 * Message-type colours, taken from the site's `chat.css` so both clients read the same.
 * `.chat-system` #008000, `.chat-info` #00bfff, `.chat-error` #ff4444, `.chat-poll` #ff8c00.
 */
private fun inkFor(type: String): Color = when (type) {
    "system" -> Color(0xFF008000)
    "info" -> Color(0xFF00BFFF)
    "error" -> Color(0xFFFF4444)
    "poll" -> Color(0xFFFF8C00)
    else -> Win98.Ink
}

/**
 * The chat toolbar, carrying the website's own icons: connect, disconnect, change nickname.
 *
 * Disabled buttons are dimmed rather than removed — a toolbar whose buttons come and go is harder
 * to read than one whose buttons grey out.
 */
@Composable
fun ChatToolbar(
    canConnect: Boolean,
    canDisconnect: Boolean,
    usersShown: Boolean,
    userCount: Int,
    status: ChatStatus,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNickname: () -> Unit,
    onToggleUsers: () -> Unit,
    onStatus: () -> Unit,
    onClear: () -> Unit,
    onQuota: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().background(Win98.Face).raised().padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarButton(R.drawable.ic_chat_connect, "Connect", canConnect, onConnect)
        ToolbarButton(R.drawable.ic_chat_disconnect, "Disconnect", canDisconnect, onDisconnect)
        ToolbarSeparator()
        ToolbarButton(R.drawable.ic_chat_nickname, "Change nickname", true, onNickname)
        ToolbarButton(R.drawable.ic_chat_options, "Chat options", true, onOptions)
        ToolbarSeparator()
        StatusButton(status, onStatus)
        ToolbarSeparator()
        ToolbarButton(
            R.drawable.ic_chat_users,
            "Show online users",
            enabled = true,
            onClick = onToggleUsers,
            held = usersShown,
            label = "$userCount",
        )
        ToolbarSeparator()
        ToolbarButton(R.drawable.ic_chat_clear, "Clear chat", true, onClear)
        ToolbarButton(R.drawable.ic_chat_quota, "Upload quota", true, onQuota)
    }
}

/** Previews under a message: pictures inline, everything else a chip that opens elsewhere. */
@Composable
fun MediaPreviews(
    links: List<MediaLinks.Link>,
    onOpen: (String) -> Unit,
    fetchAudioTags: (suspend (String) -> AudioTags?)? = null,
    modifier: Modifier = Modifier,
) {
    if (links.isEmpty()) return
    Column(modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
        links.forEach { link ->
            when (link.kind) {
                MediaLinks.Kind.IMAGE ->
                    AsyncImage(
                        model = link.url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .heightIn(max = 180.dp)
                            .sunkenDeep()
                            .padding(2.dp)
                            .tappable { onOpen(link.url) },
                    )

                MediaLinks.Kind.LINK ->
                    // A recognised platform gets a tap-to-open badge; a plain link is just its
                    // (now clickable) text.
                    link.platform?.let { PlatformBadge(it, link.url, onOpen) } ?: Unit

                else ->
                    if (link.kind == MediaLinks.Kind.AUDIO && link.isUpload && fetchAudioTags != null) {
                        AudioChip(link, onOpen, fetchAudioTags)
                    } else {
                        MediaChip(link, onOpen)
                    }
            }
        }
    }
}

/** An uploaded audio chip that also fetches and shows its sidecar tags, inside the same box. */
@Composable
private fun AudioChip(link: MediaLinks.Link, onOpen: (String) -> Unit, fetch: suspend (String) -> AudioTags?) {
    var tags by remember(link.url) { mutableStateOf<AudioTags?>(null) }
    LaunchedEffect(link.url) { tags = fetch(link.url) }
    MediaChip(link, onOpen, tags)
}

/**
 * Audio, video and PDFs open in whatever app the phone already uses for them.
 *
 * Embedding players here would mean a second playback stack fighting the radio for the audio
 * focus this app exists to hold.
 */
@Composable
private fun MediaChip(link: MediaLinks.Link, onOpen: (String) -> Unit, tags: AudioTags? = null) {
    val label = when (link.kind) {
        MediaLinks.Kind.AUDIO -> "♪ audio"
        MediaLinks.Kind.VIDEO -> "▶ video"
        else -> "file"
    }
    val name = link.url.substringAfterLast('/').substringBefore('?')
    Column(
        Modifier.padding(vertical = 2.dp).background(Win98.Face).pressable { onOpen(link.url) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text("$label  $name", color = Win98.Ink, fontFamily = W95FA, fontSize = 11.sp, maxLines = 1)
        // Artist / Title / Album rows sit inside the same box, as the website shows them.
        tags?.let { t ->
            listOfNotNull(
                t.artist?.let { "Artist" to it },
                t.title?.let { "Title" to it },
                t.album?.let { "Album" to it },
            ).forEach { (l, v) ->
                Row(Modifier.padding(top = 1.dp)) {
                    Text("$l ", color = Win98.InkDim, fontFamily = W95FA, fontSize = 10.sp)
                    Text(v, color = Win98.Ink, fontFamily = W95FA, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

/** An offline badge for a recognised platform link: glyph + name + shortened URL, tap opens it. */
@Composable
private fun PlatformBadge(platform: MediaLinks.Platform, url: String, onOpen: (String) -> Unit) {
    val label = if (platform.glyph.isEmpty()) platform.label else "${platform.glyph} ${platform.label}"
    val shortUrl = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
    Row(
        Modifier.padding(vertical = 2.dp).background(Win98.Face).pressable { onOpen(url) }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label  $shortUrl", color = Win98.Ink, fontFamily = W95FA, fontSize = 11.sp, maxLines = 1)
    }
}

/**
 * The live upload readout — percentage, bytes, speed and a Win98 bar. Shown wherever an upload can
 * be started (the channel and each PM window), so a PM upload gets the same feedback as the channel.
 */
@Composable
fun UploadProgress(progress: UploadClient.Progress?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
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

/** A LED in the status colour with its label — the site's `.chat-status-btn`. */
@Composable
private fun StatusButton(status: ChatStatus, onClick: () -> Unit) {
    Row(
        Modifier.background(Win98.Face).pressable(onClick).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusLed(status)
        Spacer(Modifier.width(4.dp))
        Text(status.label, color = Win98.Ink, fontFamily = W95FA, fontSize = 10.sp)
    }
}

/** The round status light: a filled dot with a black rim, as the site draws it. */
@Composable
fun StatusLed(status: ChatStatus, size: Dp = 8.dp) {
    Box(
        Modifier.size(size)
            .clip(CircleShape)
            .background(Color(status.led))
            .drawBehind {
                drawCircle(Color.Black, style = Stroke(1.dp.toPx()), radius = this.size.minDimension / 2f - 0.5f)
            },
    )
}

@Composable
private fun ToolbarButton(
    iconRes: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    held: Boolean = false,
    label: String? = null,
) {
    // pressable() owns remembered state, so it is called unconditionally and the disabled state is
    // expressed by swallowing the click rather than by swapping the modifier.
    Row(
        Modifier.background(Win98.Face)
            .pressable { if (enabled) onClick() }
            .then(if (held) Modifier.sunkenDeep() else Modifier)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(22.dp).alpha(if (enabled) 1f else 0.35f),
        )
        if (label != null) {
            Spacer(Modifier.width(3.dp))
            Text(label, color = Win98.Ink, fontFamily = W95FA, fontSize = 10.sp)
        }
    }
}

/** The site's `.toolbar-separator`: a sunken hairline between button groups. */
@Composable
private fun ToolbarSeparator() {
    Spacer(Modifier.width(2.dp).height(22.dp).sunken())
}

/**
 * The channel selector, followed by a button per open private conversation.
 *
 * Scrolls horizontally: a handful of open PMs will outgrow a phone's width, and a row that clips
 * its own buttons is worse than one that slides.
 */
@Composable
fun ChannelBar(
    active: ChatChannel,
    unread: Set<ChatChannel>,
    onPick: (ChatChannel) -> Unit,
    pmThreads: List<String>,
    pmUnread: Set<String>,
    activePm: String?,
    onPickPm: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChatChannel.entries.forEach { channel ->
            TabButton(
                label = channel.label,
                held = channel == active && activePm == null,
                flashing = channel in unread,
                onClick = { onPick(channel) },
            )
        }
        pmThreads.forEach { nick ->
            TabButton(
                label = "@$nick",
                held = nick == activePm,
                flashing = nick in pmUnread,
                onClick = { onPickPm(nick) },
            )
        }
    }
}

/** One selector button. [held] draws it stuck down; [flashing] adds the unread dot. */
@Composable
private fun TabButton(label: String, held: Boolean, flashing: Boolean, onClick: () -> Unit) {
    // pressable() owns remembered state, so it must be called unconditionally — putting it in one
    // branch of an if would shift its slot every time the selection changes. The held look comes
    // from painting sunkenDeep() over the raised bevel afterwards.
    val press = Modifier.pressable(onClick)
    Row(
        Modifier
            .background(Win98.Face)
            .then(press)
            .then(if (held) Modifier.sunkenDeep() else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Win98.Ink,
            fontFamily = W95FA,
            fontSize = 11.sp,
            fontWeight = if (held) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        if (flashing) {
            Spacer(Modifier.width(4.dp))
            BlinkingDot()
        }
    }
}

/** The unread cue, matching the site's blinking orange badge. */
@Composable
private fun BlinkingDot() {
    val transition = rememberInfiniteTransition(label = "unread")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "unread-alpha",
    )
    Spacer(Modifier.size(7.dp).alpha(alpha).clip(CircleShape).background(UnreadDot))
}

/**
 * One line, mIRC style: `<nick> text`.
 *
 * The nickname takes the colour the site would give it — the same hash over the same palette — so
 * a person looks the same in the app as in the browser. Server notices get their own colour and no
 * nickname at all.
 */
@Composable
fun ChatLine(
    msg: ChatMessage,
    colors: Map<String, String>,
    mentionNicks: List<String> = emptyList(),
    me: String? = null,
    badge: UserRoster.Badge = UserRoster.Badge.NONE,
    onOpenLink: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Every kind of line goes through the same emote rendering. Splitting the MOTD and the notices
    // into their own plain-Text branches is what left their emotes showing as raw `:D`.
    val serverVoiced = msg.isMotd || msg.isSystem || msg.type in NOTICE_TYPES

    // Computed unconditionally. A `remember` inside one branch of a when shifts its slot whenever a
    // reused row changes kind — which is how LazyColumn recycles — and that broke MOTD rendering
    // outright rather than merely misdrawing it.
    val nickPrefix: AnnotatedString? = remember(msg.user, msg.type, serverVoiced, colors, badge) {
        when {
            // MOTD lines are the server talking, not a user: no `<nick>`, no bullet.
            msg.isMotd -> null
            serverVoiced -> AnnotatedString("* ")
            else -> {
                val nickColor = Color(NickColors.forNick(msg.user, colors).toColorInt())
                buildAnnotatedString {
                    // The rank glyph the user list shows, now on the line too: a red @ for admins, a
                    // green + for bots, a blue + for voiced nicks — matching the website.
                    when (badge) {
                        UserRoster.Badge.ADMIN -> withStyle(SpanStyle(color = AdminRed, fontWeight = FontWeight.Bold)) { append("@") }
                        UserRoster.Badge.BOT -> withStyle(SpanStyle(color = BotGreen, fontWeight = FontWeight.Bold)) { append("+") }
                        UserRoster.Badge.VOICE -> withStyle(SpanStyle(color = VoiceBlue, fontWeight = FontWeight.Bold)) { append("+") }
                        UserRoster.Badge.NONE -> {}
                    }
                    withStyle(SpanStyle(color = nickColor, fontWeight = FontWeight.Bold)) {
                        append("<${msg.user}>")
                    }
                    append(" ")
                }
            }
        }
    }

    // The `[HH:mm]` stamp, dimmed and set apart from the nick, exactly as the site's `.chat-time`.
    val showTime = LocalChatShowTimestamps.current
    val prefix: AnnotatedString? = remember(nickPrefix, showTime, msg.timestamp) {
        if (!showTime) nickPrefix
        else buildAnnotatedString {
            withStyle(SpanStyle(color = Win98.InkDim)) { append("[${chatTimeFormat.format(java.util.Date(msg.timestamp))}] ") }
            nickPrefix?.let { append(it) }
        }
    }

    EmoteText(
        text = msg.text,
        prefix = prefix,
        color = if (serverVoiced) inkFor(msg.type) else Win98.Ink,
        bold = serverVoiced,
        // Server notices are not conversation, so an "@name" in "X joined" must not light up.
        mentionNicks = if (serverVoiced) emptyList() else mentionNicks,
        me = me,
        onOpenLink = onOpenLink,
        modifier = modifier,
    )
}

/**
 * Text with the JOI3 emotes drawn inline, so they flow and wrap with the words instead of forcing
 * the line into a Row of separate pieces.
 */
@Composable
private fun EmoteText(
    text: String,
    prefix: AnnotatedString?,
    color: Color,
    bold: Boolean,
    mentionNicks: List<String> = emptyList(),
    me: String? = null,
    onOpenLink: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tokens = remember(text) { EmoteParser.parse(text) }
    val scale = LocalChatFontScale.current
    val emoteSize = EMOTE_SIZE * scale
    val dark = Win98.palette.isDark
    val mentionColor = if (dark) Color(0xFF6EB5FF) else Color(0xFF000080)
    val selfColor = if (dark) Color(0xFFFFDD57) else Color(0xFF000080)
    val selfBg = if (dark) Color(0x26FFFF64) else Color(0xFFFFFFCC)
    val linkColor = Win98.Link
    val inline = remember(tokens, emoteSize) {
        tokens.filterIsInstance<EmoteParser.Token.Emote>()
            .map { it.emote.file }
            .distinct()
            .associateWith { file ->
                InlineTextContent(
                    Placeholder(
                        width = emoteSize,
                        height = emoteSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    AsyncImage(
                        model = "file:///android_asset/emotes/$file",
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
    }

    Text(
        buildAnnotatedString {
            if (prefix != null) append(prefix)
            tokens.forEach { token ->
                when (token) {
                    is EmoteParser.Token.Text -> {
                        // Reuse the existing mention/plain rendering for any run of non-URL text.
                        fun appendProse(value: String) {
                            if (mentionNicks.isEmpty()) {
                                append(value)
                            } else {
                                MentionParser.parse(value, mentionNicks, me).forEach { m ->
                                    when (m) {
                                        is MentionParser.Token.Text -> append(m.value)
                                        is MentionParser.Token.Mention -> withStyle(
                                            SpanStyle(
                                                color = if (m.isSelf) selfColor else mentionColor,
                                                fontWeight = FontWeight.Bold,
                                                background = if (m.isSelf) selfBg else Color.Unspecified,
                                            ),
                                        ) { append("@${m.nick}") }
                                    }
                                }
                            }
                        }

                        val spans = MediaLinks.spans(token.value)
                        var cursor = 0
                        spans.forEach { span ->
                            if (span.start > cursor) appendProse(token.value.substring(cursor, span.start))
                            withLink(
                                LinkAnnotation.Url(
                                    span.url,
                                    styles = TextLinkStyles(
                                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                    ),
                                    linkInteractionListener = { onOpenLink(span.url) },
                                ),
                            ) { append(token.value.substring(span.start, span.end)) }
                            cursor = span.end
                        }
                        if (cursor < token.value.length) appendProse(token.value.substring(cursor))
                    }
                    is EmoteParser.Token.Emote ->
                        appendInlineContent(token.emote.file, token.emote.shortcut)
                }
            }
        },
        inlineContent = inline,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        color = color,
        fontFamily = W95FA,
        fontSize = 12.sp * scale,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

/** 24sp against 12sp text: the site shows the 32px art at 24px, and the ratio carries over. */
private val EMOTE_SIZE = 24.sp

/**
 * The suggestion bar above the composer, the site's inline autocomplete: a scrolling row of chips
 * for the `@mention` or `:emote:` being typed. An emote chip shows its art and shortcut; a mention
 * chip shows `@nick`. Tapping one inserts it.
 */
@Composable
fun AutocompleteBar(
    suggestions: List<ChatAutocomplete.Suggestion>,
    onPick: (ChatAutocomplete.Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    val scale = LocalChatFontScale.current
    Row(
        modifier.fillMaxWidth().background(Win98.Face).raised().padding(3.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        suggestions.forEach { s ->
            Row(
                Modifier.background(Win98.Face).raised()
                    .pressable { onPick(s) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (s) {
                    is ChatAutocomplete.Suggestion.Emote -> {
                        AsyncImage(
                            model = s.emote.assetPath,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            s.emote.shortcut,
                            color = Win98.Ink, fontFamily = W95FA, fontSize = 11.sp * scale, maxLines = 1,
                        )
                    }
                    is ChatAutocomplete.Suggestion.Mention ->
                        Text(
                            "@${s.nick}",
                            color = Win98.Ink, fontFamily = W95FA, fontSize = 11.sp * scale,
                            fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                }
            }
        }
    }
}

/** The emote picker: the site's palette in a grid, tapping one appends its shortcut. */
@Composable
fun EmotePicker(onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(36.dp),
        modifier = modifier.fillMaxWidth().height(160.dp)
            .background(Win98.Sunken).sunkenDeep().padding(3.dp),
    ) {
        items(Emotes.PALETTE, key = { it.shortcut }) { emote ->
            Box(
                // No bevel: 122 emotes each framed as a button is a wall of edges, and the site's
                // picker is plain images too.
                Modifier.padding(2.dp).size(32.dp).tappable { onPick(emote.shortcut) },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = emote.assetPath,
                    contentDescription = emote.shortcut,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

/** A role badge before a nickname: a bold, coloured `@` or `+`, tight against the name. */
@Composable
private fun RolePrefix(symbol: String, color: Color) {
    Text(
        symbol,
        color = color,
        fontFamily = W95FA,
        fontSize = 11.sp * LocalChatFontScale.current,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(end = 2.dp),
    )
}

/** The online users, in their own colours — the site's user list, as a panel. */
@Composable
fun UserListPanel(
    users: List<ChatUser>,
    colors: Map<String, String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalChatFontScale.current
    Column(
        modifier.fillMaxWidth().background(Win98.Sunken).sunkenDeep().padding(4.dp),
    ) {
        Text(
            "Online — ${users.size}",
            color = Win98.Ink, fontFamily = W95FA, fontSize = 10.sp * scale,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        if (users.isEmpty()) {
            Text("(nobody)", color = Win98.InkDim, fontFamily = W95FA, fontSize = 11.sp * scale)
        } else {
            // Admins first, then bots, then voice, then the rest — the site's own order.
            UserRoster.sorted(users).forEach { user ->
                // Tapping a name opens a private conversation with them — the only way in, since
                // there is no other affordance for starting one.
                Row(
                    Modifier.fillMaxWidth()
                        .tappable { onPick(user.nickname) }
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusLed(ChatStatus.fromWire(user.status))
                    Spacer(Modifier.width(5.dp))
                    // The badge and its colour follow the website exactly: a red @ for admins, a
                    // green + for bridge bots, a blue + for voiced nicks. The name colour still comes
                    // from the user's own pick — except a bot, which the site paints green too.
                    when (UserRoster.badge(user)) {
                        UserRoster.Badge.ADMIN -> RolePrefix("@", AdminRed)
                        UserRoster.Badge.BOT -> RolePrefix("+", BotGreen)
                        UserRoster.Badge.VOICE -> RolePrefix("+", VoiceBlue)
                        UserRoster.Badge.NONE -> {}
                    }
                    Text(
                        user.nickname,
                        color = if (user.bot) BotGreen
                            else Color(NickColors.forNick(user.nickname, colors).toColorInt()),
                        fontFamily = W95FA,
                        fontSize = 11.sp * scale,
                    )
                }
            }
        }
    }
}

/** The composer: the field first, then emote, attach and Send along its right edge. */
@Composable
fun ChatInput(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValue: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    emotesShown: Boolean,
    onToggleEmotes: () -> Unit,
    uploadsEnabled: Boolean,
    onUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalChatFontScale.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.weight(1f).background(Win98.Sunken).sunken()
                .padding(horizontal = 6.dp, vertical = 8.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                enabled = enabled,
                singleLine = true,
                // Without these the IME falls back to its default action, which only closes the
                // keyboard — the message stayed in the field and looked lost. `onSend` deliberately
                // does not hide the keyboard: on the website you keep typing after sending, and
                // dismissing it between every line would make a conversation unusable.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                textStyle = TextStyle(color = Win98.Ink, fontFamily = W95FA, fontSize = 12.sp * scale),
                cursorBrush = SolidColor(Win98.Ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(4.dp))
        // The site uses the `:)` emote itself as the picker's button; so does this.
        Box(
            Modifier.background(Win98.Face)
                .pressable(onToggleEmotes)
                .then(if (emotesShown) Modifier.sunkenDeep() else Modifier)
                .padding(5.dp),
        ) {
            AsyncImage(
                model = "file:///android_asset/emotes/Emojis_32x32_327.png",
                contentDescription = "Emotes",
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.background(Win98.Face)
                .pressable { if (uploadsEnabled) onUpload() }
                .padding(5.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_chat_upload),
                contentDescription = "Attach a file",
                modifier = Modifier.size(20.dp).alpha(if (uploadsEnabled) 1f else 0.35f),
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            Modifier.background(Win98.Face).pressable(onSend)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text("Send", color = Win98.Ink, fontFamily = W95FA, fontSize = 11.sp)
        }
    }
}

/**
 * A file picked but not yet sent, shown above the composer with a ✕ to drop it — the same staging
 * the website gives paste/drop uploads. The file leaves on the next Send; the text field stays free
 * for a message to go alongside it.
 */
@Composable
fun UploadStagingBar(name: String, size: Long, isImage: Boolean, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Win98.Sunken).sunken()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sizeLabel = when {
            size <= 0 -> ""
            size >= 1024 * 1024 -> "  (%.1f MB)".format(size / (1024.0 * 1024.0))
            else -> "  (%.0f KB)".format(size / 1024.0)
        }
        Text(
            (if (isImage) "🖼 " else "📎 ") + name + sizeLabel,
            color = Win98.Ink, fontFamily = W95FA, fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.background(Win98.Face).pressable(onClear)
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text("✕", color = Win98.Ink, fontFamily = W95FA, fontSize = 12.sp)
        }
    }
}
