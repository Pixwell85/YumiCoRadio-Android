package net.yumicoradio.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import net.yumicoradio.android.metadata.model.NowPlaying
import net.yumicoradio.android.ui.theme.Win98

/**
 * Bottom bar shown on every sub-view. Tapping the bar returns to the player screen; the
 * play/pause button consumes its own tap, so it does not also navigate.
 */
@Composable
fun MiniPlayer(
    np: NowPlaying,
    playing: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .background(Win98.Face)
            .raised()
            .clickable { onOpen() }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = np.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(40.dp).background(Color(0xFF2A1C40)).sunkenDeep(),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                np.title.ifBlank { "Yumi Co. Radio" },
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Win98.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (np.artist.isNotBlank()) {
                Text(
                    np.artist, fontSize = 11.sp, color = Win98.InkDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Win98Button(if (playing) "❚❚" else "▶") { onToggle() }
    }
}
