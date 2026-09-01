// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Type

/**
 * One menu item. [dimmed] greys the label without blocking the tap — the Chat item is dimmed but
 * still opens a "coming soon" dialog.
 */
data class TabItem(
    val label: String,
    val dimmed: Boolean = false,
    val children: List<TabItem> = emptyList(),
    val onClick: () -> Unit,
)

/**
 * The window menu bar: a flat strip of labels running flush under the title bar.
 *
 * Deliberately **no bevel**. A menu bar is part of the window frame, not a control sitting on it —
 * give it a raised edge and it reads as one wide button, which is exactly how it looked before.
 * Depth here comes from the window's own frame, nothing else.
 *
 * 30dp is under Android's 48dp touch guidance, and that is a deliberate fidelity call: the items
 * stay wide horizontally, which keeps them hittable.
 */
@Composable
fun TabBar(items: List<TabItem>, modifier: Modifier = Modifier) {
    // A hairline under the strip, the way a Win9x menu bar is set off from the client area below it.
    val divider = Win98.Shadow
    var openMenu by remember { mutableStateOf<String?>(null) }
    Row(
        modifier.fillMaxWidth().background(Win98.Face)
            .height(30.dp)
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(divider, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            // Unweighted items can outgrow the bar at large system font scales or on a narrow
            // device. Scrolling is how the site's own task strip handles the same overflow.
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Keyed: Shell prepends a "◀" item off the player screen, so a positional remember would
        // hand slot 0's press state to a different label mid-navigation.
        items.forEach { item ->
            key(item.label) {
                MenuItem(
                    item = item,
                    expanded = openMenu == item.label,
                    onExpandedChange = { expanded ->
                        openMenu = if (expanded) item.label else null
                    },
                )
            }
        }
    }
}

/**
 * A menu item highlights; it does not depress. That is why this does not use [pressable] — the
 * press state here is a colour swap (navy fill, white text), the Win9x menu idiom.
 */
@Composable
private fun MenuItem(
    item: TabItem,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // A pressed dimmed item goes white like any other: Shadow grey on the navy fill is unreadable, and the
    // dimming exists to say "not ready yet", not to say "not tappable".
    val fg = when {
        pressed || expanded -> Color.White
        item.dimmed -> Win98.InkDim
        else -> Win98.Ink
    }
    Box {
        Text(
            item.label,
            fontFamily = W95FA,
            fontSize = Win98Type.Body,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .height(30.dp) // fills the bar, so the highlight covers the full menu height
                .background(if (pressed || expanded) Win98.DialogBlue else Color.Transparent)
                .clickable(interactionSource = interactionSource, indication = null) {
                    if (item.children.isEmpty()) item.onClick() else onExpandedChange(!expanded)
                }
                .padding(horizontal = if (item.children.isEmpty()) 7.dp else 9.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        )

        if (item.children.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.widthIn(min = 164.dp).background(Win98.Face),
                shape = RectangleShape,
                containerColor = Win98.Face,
                tonalElevation = 0.dp,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Win98.Shadow),
            ) {
                item.children.forEach { child ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                child.label,
                                fontFamily = W95FA,
                                fontSize = Win98Type.Body,
                                color = if (child.dimmed) Win98.InkDim else Win98.Ink,
                            )
                        },
                        onClick = {
                            onExpandedChange(false)
                            child.onClick()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(48.dp),
                    )
                }
            }
        }
    }
}
