package net.yumicoradio.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import net.yumicoradio.android.BuildConfig
import net.yumicoradio.android.R
import net.yumicoradio.android.ui.components.*
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.util.TrackTime

/** One height and one gap for the whole control deck, so the four buttons cannot drift apart. */
private val DECK_BUTTON_HEIGHT = 30.dp
private val DECK_GAP = 4.dp

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel,
    tabs: List<TabItem>,
    onShare: (String) -> Unit,
    onSleep: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    val np by vm.nowPlaying.collectAsState()
    val playing by vm.isPlaying.collectAsState()
    val quality by vm.quality.collectAsState()
    val volume by vm.volume.collectAsState()

    // The 15s poll is too coarse for a seconds readout, so tick locally between polls.
    var nowSeconds by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000); nowSeconds = System.currentTimeMillis() / 1000 }
    }

    Win98Window(
        title = "Yumi Co. Radio · v${BuildConfig.VERSION_NAME}",
        modifier = Modifier.fillMaxWidth(),
        icon = R.drawable.ic_win_player,
        onMinimize = onMinimize,
        onClose = onClose,
        menuBar = { TabBar(tabs) },
    ) {
        // Artwork hero.
        Box(Modifier.fillMaxWidth().aspectRatio(1f).sunkenDeep().padding(4.dp)) {
            AsyncImage(
                model = np.artworkUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(R.drawable.default_cover),
                error = painterResource(R.drawable.default_cover),
                fallback = painterResource(R.drawable.default_cover),
                modifier = Modifier.fillMaxSize().background(Color(0xFF000000)),
            )
        }
        Spacer(Modifier.height(8.dp))

        // LCD track panel — it also carries the timer and the bitrate, as the site's readout does.
        LcdPanel(
            artist = np.artist,
            title = np.title,
            time = TrackTime.label(np.playedAt, np.duration, nowSeconds),
            bitrate = "${quality.kbps}k",
        )
        Spacer(Modifier.height(8.dp))

        // The visualiser sits directly under the readout it belongs with, where the eye already is.
        VuMeter(volume = volume, playing = playing)
        Spacer(Modifier.height(6.dp))

        // Every control on one deck: transport first, then the two app actions. Sleep and Share
        // used to sit bare on the window face below; loose buttons beside a panelled instrument
        // were the last thing that still read as improvised.
        ControlPanel(Modifier.fillMaxWidth()) {
            // One size for all four. Sleep and Share are the widest of them, so the deck is laid out
            // in equal shares rather than left to each button's own content — four controls in three
            // sizes was the giveaway that they had been added at different times.
            val key = Modifier.weight(1f).height(DECK_BUTTON_HEIGHT)
            TransportButton(if (playing) "❚❚" else "▶", key) { vm.toggle() }
            Spacer(Modifier.width(DECK_GAP))
            TransportButton("■", key) { vm.stop() }
            Spacer(Modifier.width(DECK_GAP))
            SleepButton(key) { onSleep() }
            Spacer(Modifier.width(DECK_GAP))
            ShareButton(key) { onShare("Now playing ${np.artist} — ${np.title} on Yumi Co. Radio 📻 https://yumicoradio.net") }
        }
        Spacer(Modifier.height(6.dp))

        // The volume slider gets the full width to itself: it is the one control here you aim at
        // rather than tap, and precision costs nothing but a row.
        ControlPanel(Modifier.fillMaxWidth()) {
            Text("Vol", color = Win98.Ink, fontFamily = W95FA, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            VolumeMeterBar(volume = volume, onVolume = { vm.setVolume(it) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        // Footer status bar (unchanged idiom).
        Win98StatusBar {
            StatusField(Modifier.weight(1f)) {
                StatusLed(on = playing)
                StatusText(if (playing) "LIVE" else "Stopped")
            }
            // Time and bitrate used to sit here; they moved into the LCD panel, where the eye
            // already is. Repeating them would only make the footer noisy.
            StatusField { StatusText("Listeners: ${np.listeners}") }
        }
    }
}

/** Raised transport button. Sized by the caller so it matches the rest of the deck. */
@Composable
private fun TransportButton(glyph: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(Win98.Face).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Win98.Ink, fontFamily = W95FA, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** A sunken instrument field. The controls sit inside these rather than loose on the window face. */
@Composable
private fun ControlPanel(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.sunken().padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
