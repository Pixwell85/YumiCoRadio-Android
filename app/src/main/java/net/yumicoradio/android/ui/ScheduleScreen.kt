// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import net.yumicoradio.android.schedule.ScheduleBlock
import net.yumicoradio.android.schedule.ScheduleBuilder
import net.yumicoradio.android.schedule.Program
import net.yumicoradio.android.ui.components.raised
import net.yumicoradio.android.ui.components.sunken
import net.yumicoradio.android.ui.components.sunkenDeep
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Metrics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The programming schedule, following the website's window: this hour's trackbar, a Now / Coming Up
 * pair, and the three programme descriptions.
 *
 * The VFD header is deliberately left out — it is decoration, and a phone has no room to spare.
 */
@Composable
fun ColumnScope.ScheduleContent(schedule: ScheduleViewModel) {
    val entries by schedule.timeline.collectAsState()

    // One second, like the site: anything slower and the playhead visibly jumps. Gated on RESUMED so
    // neither the playhead tick nor the queue poll runs while the screen is not in front of the user.
    var now by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            now = System.currentTimeMillis() / 1000   // fresh on every resume, so the playhead never
            while (true) {                            // shows a stale second before the first tick
                delay(1000)
                now = System.currentTimeMillis() / 1000
            }
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            schedule.start()
            try { awaitCancellation() } finally { schedule.stop() }
        }
    }

    val hourStart = ScheduleBuilder.hourStart(now)
    val blocks = remember(entries, hourStart) { ScheduleBuilder.blocksForHour(entries, hourStart) }
    val current = blocks.firstOrNull { now in it.start until it.end }
    // Computed on the unclipped entries: a slot starting at 23:59 must report its real end, not
    // midnight. Falls back to the station's fixed grid when the queue holds nothing ahead.
    val next = remember(entries, now / 30) { ScheduleBuilder.upcoming(entries, now) }

    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "This Hour's Schedule",
                fontFamily = W95FA, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Win98.Ink,
            )
            Spacer(Modifier.width(6.dp))
            LiveBadge()
        }
        Spacer(Modifier.height(6.dp))

        TrackBar(
            blocks = blocks,
            hourStart = hourStart,
            playhead = ScheduleBuilder.playheadFraction(now, hourStart),
        )
        Spacer(Modifier.height(2.dp))
        TickLabels(hourStart)
        Spacer(Modifier.height(8.dp))

        Legend()
        Spacer(Modifier.height(4.dp))
        Text(
            "Repeats every hour",
            fontFamily = W95FA, fontSize = 10.sp, color = Win98.InkDim,
        )
        Spacer(Modifier.height(10.dp))

        NowNext(current = current, next = next)
        Spacer(Modifier.height(12.dp))

        ProgramCards()
    }
}

@Composable
private fun LiveBadge() {
    // Blinks like the site's badge, so the bar reads as live rather than as a static picture. Only
    // while the screen is in front of the user — a badge nobody can see need not toggle.
    var on by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(900)
                on = !on
            }
        }
    }
    Box(
        Modifier.background(if (on) Color(0xFFCC0000) else Color(0xFF6E0000))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("LIVE", fontFamily = W95FA, fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** The hour as programme blocks, each labelled where it is wide enough to read. */
@Composable
private fun TrackBar(blocks: List<ScheduleBlock>, hourStart: Long, playhead: Float) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(40.dp).sunkenDeep().padding(3.dp)) {
            // Drawn in one pass: every block is placed from its own fraction of the hour, so
            // nothing depends on sibling widths or layout order.
            Box(
                Modifier.fillMaxSize().background(Color(0xFF1A1A1A)).drawBehind {
                    blocks.forEach { block ->
                        drawRect(
                            Color(block.program.color),
                            topLeft = Offset(block.startFraction(hourStart) * size.width, 0f),
                            size = Size(block.widthFraction(hourStart) * size.width, size.height),
                        )
                    }
                    // Five-minute ticks over the blocks, as on the site.
                    for (i in 1 until 12) {
                        val x = size.width * i / 12f
                        drawRect(
                            Color(0x33000000),
                            topLeft = Offset(x, 0f),
                            size = Size(1f, size.height),
                        )
                    }
                    drawRect(
                        Color(0xFFFF2222),
                        topLeft = Offset(playhead * size.width - 1.5f, 0f),
                        size = Size(3f, size.height),
                    )
                },
            )
            // No labels ride inside the bar: the blocks are the colours, and the legend below names
            // them. Abbreviations like "F.Funk" printed on the bar only repeated the legend badly.
        }
    }
}

/** :00 … :60 under the bar, so a block's position means something. */
@Composable
private fun TickLabels(hourStart: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(0, 15, 30, 45, 60).forEach { minute ->
            Text(
                if (minute == 60) clock(hourStart + 3600) else ":${minute.toString().padStart(2, '0')}",
                fontFamily = W95FA, fontSize = 9.sp, color = Win98.InkDim,
            )
        }
    }
}

/** The colour sits inside the frame rather than under it, so the bevel reads as a border. */
@Composable
private fun Swatch(color: Color, size: androidx.compose.ui.unit.Dp) {
    // Inset by exactly the bevel: 3dp left a grey margin inside the frame, so the swatch read as a
    // small colour on a large border rather than as a filled chip.
    Box(
        Modifier.size(size).background(Win98.Face).sunken().padding(Win98Metrics.Bevel),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(color))
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Program.entries.forEach { program ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Swatch(Color(program.color), 16.dp)
                Spacer(Modifier.width(4.dp))
                Text(program.legend, fontFamily = W95FA, fontSize = 9.sp, color = Win98.Ink)
            }
        }
    }
}

/** The site's Now / Coming Up pair, side by side. */
@Composable
private fun NowNext(current: ScheduleBlock?, next: ScheduleBuilder.Slot) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NowNextCard(
            label = "Now Playing",
            program = current?.program,
            time = current?.let { "${clock(it.start)} – ${clock(it.end)}" },
            modifier = Modifier.weight(1f),
        )
        NowNextCard(
            label = "Coming Up",
            program = next.program,
            time = next.end?.let { "${clock(next.start)} – ${clock(it)}" }
                // The grid says what starts, never how long it runs; claiming an end would invent one.
                ?: "from ${clock(next.start)}",
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Now Playing and Coming Up wear the artwork and the ink of the programme they name, so the pair
 * ties back to the cards below and to the coloured blocks in the bar above. A grey Win9x field said
 * nothing about what was actually playing.
 *
 * With no programme to show — nothing scheduled ahead — it falls back to the plain field rather
 * than picking an arbitrary background for an empty slot.
 */
@Composable
private fun NowNextCard(
    label: String,
    program: Program?,
    time: String?,
    modifier: Modifier = Modifier,
) {
    val style = program?.let(CardStyle::of)
    Box(modifier.background(Win98.Face).raised()) {
        if (style != null) {
            AsyncImage(
                model = "file:///android_asset/schedule/${style.asset}",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier.matchParentSize()
                    .background(Brush.horizontalGradient(style.scrim)),
            )
        }
        Column(Modifier.padding(6.dp)) {
            Text(
                label,
                fontFamily = W95FA, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (style != null) style.subInk else Win98.Shadow,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                program?.label ?: "—",
                fontFamily = W95FA, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = if (style != null) style.ink else Win98.Ink, maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                time.orEmpty(),
                fontFamily = W95FA, fontSize = 9.sp,
                color = if (style != null) style.subInk else Win98.Shadow,
            )
        }
    }
}

/**
 * The three programme descriptions, matching the site's schedule grid: its artwork, its gradient
 * scrim, its text colours.
 */
@Composable
private fun ProgramCards() {
    ProgramCard(
        style = CardStyle.CITYPOP,
        slot = "On the hour",
        title = "🎵 City Pop Track + Retro Ad",
        body = "Japanese City Pop from the 80s to today, including classics, remixes, covers and " +
            "modern interpretations.",
        extra = "Paired with: authentic Japanese retro TV commercial",
    )
    Spacer(Modifier.height(8.dp))
    ProgramCard(
        style = CardStyle.FUTUREFUNK,
        slot = "Main rotation",
        title = "🎧 Future Funk & Friends",
        body = "The heart of the station — playing between each featured slot.",
        tags = listOf("Future Funk", "Anime Groove", "Nu Disco", "Synthwave", "& more…"),
    )
    Spacer(Modifier.height(8.dp))
    ProgramCard(
        style = CardStyle.VAPORWAVE,
        slot = "Half past the hour",
        title = "🌸 Vaporwave Moment",
        body = "A peaceful interlude in nostalgic sounds.",
    )
}

/**
 * One card's look, taken from the site's `.citypop-block` / `.main-block` / `.vaporwave-block`
 * rules.
 *
 * [scrim] is the gradient laid over the artwork so the text stays readable — dark for the two
 * neon-on-black cards, light for Future Funk, exactly as the site does it. [ink] is not always the
 * programme's own colour: Future Funk writes in #3c3fe7, a deeper blue than its bar block.
 */
private enum class CardStyle(
    val asset: String,
    val accent: Color,
    val ink: Color,
    val scrim: List<Color>,
    val tagInk: Color,
    // The Now/Next label and time sit over the card's own scrim, so they need a colour read against
    // *that* — not [tagInk], which is tuned for the genre tags' navy chip. On the two dark-scrim
    // cards the two happen to agree (white); Future Funk's scrim is light, so its cyan tag colour
    // vanished there and it takes a dark tone of its own.
    val subInk: Color,
) {
    CITYPOP(
        asset = "citypop6.webp",
        accent = Color(0xFFFFB347),
        ink = Color(0xFFFFB347),
        scrim = listOf(Color(0xD9000000), Color(0x80000000), Color.Transparent),
        tagInk = Color.White,
        subInk = Color.White,
    ),
    FUTUREFUNK(
        asset = "futurefunk.webp",
        accent = Color(0xFF3C7EF7),
        ink = Color(0xFF3C3FE7),
        scrim = listOf(Color(0xD9FFFFFF), Color(0x99FFFFFF), Color(0x4DFFFFFF)),
        tagInk = Color(0xFF00BFFF),
        subInk = Color(0xFF1B1E8C),
    ),
    VAPORWAVE(
        asset = "vwave.webp",
        accent = Color(0xFF00FFFF),
        ink = Color(0xFF00FFFF),
        scrim = listOf(Color(0xD9000000), Color(0x80000000), Color.Transparent),
        tagInk = Color.White,
        subInk = Color.White,
    );

    companion object {
        /** A programme's dressing, so Now/Next and the cards below cannot drift apart. */
        fun of(program: Program): CardStyle = when (program) {
            Program.CITYPOP -> CITYPOP
            Program.VAPORWAVE -> VAPORWAVE
            Program.FUTUREFUNK -> FUTUREFUNK
        }
    }
}

@Composable
private fun ProgramCard(
    style: CardStyle,
    slot: String,
    title: String,
    body: String,
    extra: String? = null,
    tags: List<String> = emptyList(),
) {
    Box(
        Modifier.fillMaxWidth().sunken()
            // The site's `border-left-color` on each block, kept as the tie back to the trackbar.
            .drawBehind {
                drawRect(
                    style.accent,
                    topLeft = Offset(0f, 0f),
                    size = Size(4.dp.toPx(), size.height),
                )
            },
    ) {
        AsyncImage(
            model = "file:///android_asset/schedule/${style.asset}",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Left-to-right scrim: the site keeps the artwork visible on the right while the text side
        // stays legible.
        Box(
            Modifier.matchParentSize()
                .background(Brush.horizontalGradient(style.scrim)),
        )
        Column(Modifier.padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp)) {
            Box(
                Modifier.background(style.accent.copy(alpha = 0.2f))
                    .drawBehind {
                        val w = 1.dp.toPx()
                        drawRect(style.accent, Offset(0f, 0f), Size(size.width, w))
                        drawRect(style.accent, Offset(0f, size.height - w), Size(size.width, w))
                        drawRect(style.accent, Offset(0f, 0f), Size(w, size.height))
                        drawRect(style.accent, Offset(size.width - w, 0f), Size(w, size.height))
                    }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    slot.uppercase(),
                    fontFamily = W95FA, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    color = style.accent,
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                fontFamily = W95FA, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = style.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(body, fontFamily = W95FA, fontSize = 11.sp, color = style.ink, lineHeight = 15.sp)
            extra?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, fontFamily = W95FA, fontSize = 10.sp, color = style.ink, lineHeight = 14.sp)
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    tags.forEach { tag ->
                        Box(
                            // The site's navy `.genre-tag` with its outset edge.
                            Modifier.background(Color(0xFF000080)).raised()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(tag, fontFamily = W95FA, fontSize = 10.sp, color = style.tagInk)
                        }
                    }
                }
            }
        }
    }
}

private fun clock(epochSeconds: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
