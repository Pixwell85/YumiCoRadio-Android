// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import net.yumicoradio.android.R
import net.yumicoradio.android.ui.components.sunkenDeep
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.util.PlayedTime

@Composable
fun ColumnScope.HistoryContent(vm: PlayerViewModel) {
    val past by vm.recent.collectAsState()
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
                        Modifier.fillMaxWidth().padding(4.dp),
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
}
