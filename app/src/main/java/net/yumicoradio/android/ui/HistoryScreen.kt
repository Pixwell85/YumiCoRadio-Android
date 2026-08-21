// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import net.yumicoradio.android.R
import net.yumicoradio.android.history.HistoryActions
import net.yumicoradio.android.metadata.model.RecentTrack
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Dialog
import net.yumicoradio.android.ui.components.sunkenDeep
import net.yumicoradio.android.ui.components.tappable
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.util.PlayedTime

@Composable
fun ColumnScope.HistoryContent(vm: PlayerViewModel) {
    val past by vm.recent.collectAsState()
    var selected by remember { mutableStateOf<RecentTrack?>(null) }
    Column(
        Modifier.fillMaxWidth().weight(1f).background(Win98.Sunken).sunkenDeep().padding(3.dp),
    ) {
        if (past.isEmpty()) {
            Text(
                "No tracks yet…", fontSize = 12.sp, color = Win98.InkDim,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(past) { t ->
                    Row(
                        Modifier.fillMaxWidth().tappable { selected = t }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = t.imageUrl, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            placeholder = painterResource(R.drawable.default_cover),
                            error = painterResource(R.drawable.default_cover),
                            fallback = painterResource(R.drawable.default_cover),
                            modifier = Modifier.size(30.dp).background(Color(0xFF2A1C40)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                color = Win98.Ink, maxLines = 1)
                            Text(t.artist, fontSize = 11.sp, color = Win98.InkDim, maxLines = 1)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            PlayedTime.label(t.uts, System.currentTimeMillis()),
                            fontSize = 10.sp, color = Win98.InkDim,
                        )
                    }
                }
            }
        }
    }

    selected?.let { track ->
        HistoryTrackDialog(track = track, onDismiss = { selected = null })
    }
}

@Composable
private fun HistoryTrackDialog(track: RecentTrack, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val query = HistoryActions.displayText(track.artist, track.title)
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
    val copy = {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("Artist - Title", query))
    }

    Win98Dialog(
        title = "Track details",
        icon = R.drawable.ic_win_history,
        onDismiss = onDismiss,
    ) {
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
