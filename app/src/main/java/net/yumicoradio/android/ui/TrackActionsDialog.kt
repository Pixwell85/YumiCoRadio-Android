// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.history.HistoryActions
import net.yumicoradio.android.ratings.MyVoteRow
import net.yumicoradio.android.ratings.RankingRow
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

internal data class TrackActionTarget(val artist: String, val title: String)

internal fun RankingRow.trackActionTarget() = TrackActionTarget(track.artist, track.title)
internal fun MyVoteRow.trackActionTarget() = TrackActionTarget(track.artist, track.title)

@Composable
internal fun TrackActionsDialog(
    track: TrackActionTarget,
    @DrawableRes icon: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val query = HistoryActions.displayText(track.artist, track.title)
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    val copy = {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Artist - Title", query))
    }

    Win98Dialog(title = "Track details", icon = icon, onDismiss = onDismiss) {
        Text(
            track.artist,
            fontFamily = W95FA,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Win98.Ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(track.title, fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink)
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Win98Button("Copy", modifier = Modifier.width(92.dp)) { copy() }
                Win98Button("YouTube", modifier = Modifier.width(92.dp)) {
                    open(HistoryActions.youtubeUrl(query))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Win98Button("Google", modifier = Modifier.width(92.dp)) {
                    open(HistoryActions.googleUrl(query))
                }
                Win98Button("Spotify", modifier = Modifier.width(92.dp)) {
                    open(HistoryActions.spotifyUrl(query))
                }
            }
        }
    }
}
