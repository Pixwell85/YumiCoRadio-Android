// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.BuildConfig
import net.yumicoradio.android.ui.components.Win98Button
import net.yumicoradio.android.ui.components.Win98Fieldset
import net.yumicoradio.android.ui.components.tappable
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98

/**
 * The website's About window, cut to fit a phone: who the station is, and the stream URLs people
 * come here for.
 *
 * The embed-player tab is left out — pasting an iframe into a website is not something anyone does
 * from a phone.
 */
@Composable
fun ColumnScope.AboutContent(vm: PlayerViewModel) {
    val uris = LocalUriHandler.current
    val updateState by vm.updateState.collectAsState()

    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
        Win98Fieldset("Yumi Co. Radio") {
            Text(
                "Welcome to Yumi Co. Radio!",
                fontFamily = W95FA, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Win98.Ink,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "A 24/7 webradio featuring Future Funk, Japanese City Pop from the 70s, 80s & 90s, " +
                    "Anime Groove, Nu Disco, a touch of Vaporwave and Synthwave, along with " +
                    "related sub-genres.",
                fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink, lineHeight = 16.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("Streaming Links") {
            Text(
                "Use these URLs to listen in your favourite media player (Winamp, foobar2000, " +
                    "VLC, MPV…) or any app or game that supports streaming.",
                fontFamily = W95FA, fontSize = 10.sp, color = Win98.Ink, lineHeight = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "HTTP links offer better compatibility with games, older players and legacy devices.",
                fontFamily = W95FA, fontSize = 9.sp, color = Win98.InkDim, lineHeight = 13.sp,
            )
            Spacer(Modifier.height(8.dp))

            StreamGroup(
                title = "MP3 256 kbps",
                https = "https://yumicoradio.net/stream",
                http = "http://s1.yumicoradio.net:8000/stream",
                onOpen = uris::openUri,
            )
            Spacer(Modifier.height(8.dp))
            StreamGroup(
                title = "MP3 128 kbps",
                https = "https://yumicoradio.net/stream_128",
                http = "http://s1.yumicoradio.net:8000/stream_128",
                onOpen = uris::openUri,
            )
            Spacer(Modifier.height(8.dp))
            StreamGroup(
                title = "AAC 64 kbps",
                https = "https://yumicoradio.net/stream_aac64",
                http = "http://s1.yumicoradio.net:8000/stream_aac64",
                onOpen = uris::openUri,
            )
        }

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("Playlist Files") {
            Text(
                "Download a playlist file to quickly add Yumi Co. Radio to your media player.",
                fontFamily = W95FA, fontSize = 10.sp, color = Win98.Ink, lineHeight = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            PlaylistRow(
                badge = "M3U",
                badgeColor = Color(0xFF008080),
                file = "playlist.m3u",
                url = "https://yumicoradio.net/public/yumi_co._radio/playlist.m3u",
                compat = "Winamp, VLC, MPV",
                onOpen = uris::openUri,
            )
            Spacer(Modifier.height(4.dp))
            PlaylistRow(
                badge = "PLS",
                badgeColor = Color(0xFF800080),
                file = "playlist.pls",
                url = "https://yumicoradio.net/public/yumi_co._radio/playlist.pls",
                compat = "foobar2000",
                onOpen = uris::openUri,
            )
        }

        Spacer(Modifier.height(10.dp))

        Win98Fieldset("This App") {
            Text(
                "The official Yumi Co. Radio app: the station's player, its live chat and its " +
                    "programming schedule, in the same Windows 9x dress as the website.",
                fontFamily = W95FA, fontSize = 11.sp, color = Win98.Ink, lineHeight = 16.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                fontFamily = W95FA, fontSize = 10.sp, color = Win98.InkDim,
            )
            Spacer(Modifier.height(6.dp))
            Win98Button(
                label = if (updateState is net.yumicoradio.android.update.UpdateState.Checking) {
                    "Checking F-Droid..."
                } else {
                    "Check for updates"
                },
                enabled = updateState !is net.yumicoradio.android.update.UpdateState.Checking,
                onClick = { vm.checkForUpdates() },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Free software under the GPLv3. No trackers, no analytics, no ads.",
                fontFamily = W95FA, fontSize = 10.sp, color = Win98.InkDim, lineHeight = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Link("yumicoradio.net", "https://yumicoradio.net", uris::openUri)
            Link(
                "Source code on GitHub",
                "https://github.com/Pixwell85/yumicoradio-android",
                uris::openUri,
            )
        }
    }
}

/** One bitrate with its HTTPS and HTTP addresses — the site's nested stream fieldset. */
@Composable
private fun StreamGroup(title: String, https: String, http: String, onOpen: (String) -> Unit) {
    Win98Fieldset(title) {
        StreamRow("HTTPS", Color(0xFF006600), https, onOpen)
        Spacer(Modifier.height(3.dp))
        StreamRow("HTTP", Color(0xFF7A5200), http, onOpen)
    }
}

@Composable
private fun StreamRow(protocol: String, badgeColor: Color, url: String, onOpen: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(protocol, badgeColor)
        Spacer(Modifier.width(6.dp))
        Link(url, url, onOpen, size = 10)
    }
}

@Composable
private fun PlaylistRow(
    badge: String,
    badgeColor: Color,
    file: String,
    url: String,
    compat: String,
    onOpen: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Badge(badge, badgeColor)
        Spacer(Modifier.width(6.dp))
        Link(file, url, onOpen, size = 11)
        Spacer(Modifier.width(6.dp))
        Text(compat, fontFamily = W95FA, fontSize = 9.sp, color = Win98.InkDim)
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(Modifier.background(color).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(
            text,
            fontFamily = W95FA, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
        )
    }
}

@Composable
private fun Link(label: String, url: String, onOpen: (String) -> Unit, size: Int = 12) {
    Text(
        label,
        fontFamily = W95FA,
        fontSize = size.sp,
        color = Win98.Link,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.tappable { onOpen(url) }.padding(vertical = 2.dp),
    )
}
