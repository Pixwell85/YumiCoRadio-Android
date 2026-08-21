// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import android.graphics.drawable.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import net.yumicoradio.android.chat.ChatMediaPolicy
import net.yumicoradio.android.chat.MediaLinks

/** Inline chat image whose animated drawable runs only while its message can actually be seen. */
@Composable
fun ChatImagePreview(
    link: MediaLinks.Link,
    messageVisible: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var foreground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val painter = rememberAsyncImagePainter(link.url)
    val animatable = (painter.state as? AsyncImagePainter.State.Success)
        ?.result?.drawable as? Animatable
    val isGif = remember(link) { ChatMediaPolicy.isAnimatedGif(link) }
    val allowAnimation = ChatMediaPolicy.shouldAnimateGif(isGif, messageVisible, foreground)

    DisposableEffect(animatable, allowAnimation) {
        if (allowAnimation) animatable?.start() else animatable?.stop()
        onDispose { animatable?.stop() }
    }

    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .padding(vertical = 2.dp)
            .heightIn(max = 180.dp)
            .sunkenDeep()
            .padding(2.dp)
            .tappable { onOpen(link.url) },
    )
}
