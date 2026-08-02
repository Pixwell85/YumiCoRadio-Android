// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.ui.theme.W95FA

internal val LcdBg = Color(0xFF0A1628)
private val LcdGreen = Color(0xFF00E676)
private val LcdCyan = Color(0xFF4FC3F7)
private val Scanline = Color(0x0800FF80) // rgba(0,255,128,0.03)

// The footer line, held back from the two readouts above it so the track stays the loud thing.
private val LcdGreenDim = Color(0xFF00A152)
private val LcdCyanDim = Color(0xFF3B8FB8)
// A third, amber tone for the EQ readout: it sits between the green timer and the cyan bitrate, and
// a distinct hue keeps the three from reading as one run of text.
private val LcdAmberDim = Color(0xFFB08D3A)

/**
 * The dark VFD well the readouts sit in: a sunken frame, near-black navy, and faint scanlines.
 *
 * Shared so every display in the player stands in the same material — the visualiser included.
 * Before this, the meter sat in a grey Win9x field while the track sat in a black one, and the two
 * read as parts of different machines.
 */
fun Modifier.lcdWell(): Modifier = this
    .sunkenDeep()
    .background(LcdBg)
    .drawWithContent {
        drawContent()
        var y = 0f
        val gap = 3.dp.toPx()
        while (y < size.height) {
            drawRect(
                Scanline,
                topLeft = androidx.compose.ui.geometry.Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()),
            )
            y += gap
        }
    }

/**
 * The black LCD readout from the site's mini player: bold green artist over a cyan title,
 * a dark navy well, and faint horizontal scanlines drawn over the whole panel.
 *
 * [time] ("1:17 / 2:48") and [bitrate] share a dim footer line inside the same well. On the website
 * these live in the `.track-progress` display, which carries the time readout and the stream label
 * together (`index.html:663`) — a readout is where you look for what is playing and how far in, so
 * that is where they belong here too, rather than in the window's status bar.
 *
 * The footer line is omitted entirely when there is nothing to show, so a live set with no finite
 * duration does not leave an empty band in the panel.
 */
@Composable
fun LcdPanel(
    artist: String,
    title: String,
    modifier: Modifier = Modifier,
    time: String? = null,
    bitrate: String? = null,
    eq: String? = null,
    onTap: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .lcdWell()
            .then(if (onTap != null) Modifier.tappable(onTap) else Modifier)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            artist.ifBlank { "Yumi Co. Radio" },
            color = LcdGreen, fontFamily = W95FA, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            title.ifBlank { "Stopped" },
            color = LcdCyan, fontFamily = W95FA,
            fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (time != null || bitrate != null || eq != null) {
            Spacer(Modifier.height(3.dp))
            // Three equal thirds, each field anchored in its own: timer left, EQ centred, bitrate
            // right. Fixed columns are what keeps the EQ pinned dead-centre — the timer's width shifts
            // by a pixel every second as its digits change, and a flow layout would drag the EQ along
            // with it.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Dimmed against the artist and title: this line is for glancing at, not reading.
                Text(
                    time.orEmpty(),
                    color = LcdGreenDim, fontFamily = W95FA, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    eq.orEmpty(),
                    color = LcdAmberDim, fontFamily = W95FA, fontSize = 11.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    bitrate.orEmpty(),
                    color = LcdCyanDim, fontFamily = W95FA, fontSize = 11.sp, maxLines = 1,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
