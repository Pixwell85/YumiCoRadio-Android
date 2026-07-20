package net.yumicoradio.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.ui.theme.Win98

/** Win9x status bar: a row of sunken fields separated by a 1dp gutter. */
@Composable
fun Win98StatusBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        content = content,
    )
}

/** One sunken field. Pass `Modifier.weight(1f)` for the field that should absorb spare width. */
@Composable
fun StatusField(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.height(20.dp).background(Win98.Face).sunken().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content,
    )
}

/** Status bar text: 10sp, single line, ellipsised — matches the site's 10px fields. */
@Composable
fun StatusText(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 10.sp, color = Win98.Ink,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier,
    )
}

/** 6dp indicator, dark when off, green when on — the site's `.mini-status-led`. */
@Composable
fun StatusLed(on: Boolean) {
    Box(
        Modifier.size(6.dp).background(
            if (on) Color(0xFF00E676) else Color(0xFF333333),
            CircleShape,
        )
    )
}
