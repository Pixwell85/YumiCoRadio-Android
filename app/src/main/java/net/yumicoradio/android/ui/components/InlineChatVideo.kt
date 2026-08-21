// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import android.graphics.Color
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import net.yumicoradio.android.chat.MediaLinks
import net.yumicoradio.android.chat.ChatVideoControlsState
import net.yumicoradio.android.ui.theme.W95FA
import net.yumicoradio.android.ui.theme.Win98
import net.yumicoradio.android.ui.theme.Win98Type

/** Snapshot and actions supplied by the one Chat-screen video session. */
data class InlineVideoBinding(
    val activeKey: String?,
    val activeUrl: String?,
    val fullscreenKey: String?,
    val player: Player?,
    val errorKey: String?,
    val volume: Float,
    val play: (String, String) -> Unit,
    val updateVisibility: (String, Boolean) -> Unit,
    val enterFullscreen: (String) -> Unit,
    val exitFullscreen: () -> Unit,
    val setVolume: (Float) -> Unit,
    val toggleMute: () -> Unit,
    val openExternal: (String, String) -> Unit,
)

/** A direct video attachment rendered and played inside its own chat message. */
@Composable
fun InlineChatVideo(
    instanceKey: String,
    link: MediaLinks.Link,
    binding: InlineVideoBinding,
    messageVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = binding.activeKey == instanceKey
    val fullscreen = binding.fullscreenKey == instanceKey
    val failed = binding.errorKey == instanceKey
    val controlsState = remember(instanceKey) { ChatVideoControlsState() }
    var controlsVisible by remember(instanceKey) { mutableStateOf(controlsState.visible) }
    var playerView by remember(instanceKey) { mutableStateOf<PlayerView?>(null) }
    val syncControls: (Boolean) -> Unit = { isVisible ->
        controlsState.controllerVisibilityChanged(isVisible)
        controlsVisible = controlsState.visible
    }
    val keepControlsVisible: () -> Unit = {
        playerView?.showController()
        syncControls(true)
    }
    val controlsInteraction: (Boolean) -> Unit = { activeInteraction ->
        controlsState.interactionChanged(activeInteraction)
        controlsVisible = controlsState.visible
        if (!activeInteraction) playerView?.showController()
    }

    LaunchedEffect(active, messageVisible, instanceKey) {
        if (active) binding.updateVisibility(instanceKey, messageVisible)
    }
    DisposableEffect(active, instanceKey) {
        onDispose {
            if (active) binding.updateVisibility(instanceKey, false)
        }
    }

    Box(
        modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(ComposeColor.Black)
            .sunkenDeep()
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            failed -> Column(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "This video could not be played on this device.",
                    color = Win98.Error,
                    fontFamily = W95FA,
                    fontSize = Win98Type.Body,
                )
                Win98Button("Open externally") { binding.openExternal(instanceKey, link.url) }
            }

            active && binding.player != null && !fullscreen -> Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            playerView = this
                            setPlayer(binding.player)
                            useController = true
                            controllerAutoShow = true
                            controllerShowTimeoutMs = ChatVideoControlsState.AUTO_HIDE_MILLIS
                            setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    syncControls(visibility == View.VISIBLE)
                                },
                            )
                            showController()
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShutterBackgroundColor(Color.BLACK)
                            setFullscreenButtonClickListener { requested ->
                                if (requested) binding.enterFullscreen(instanceKey)
                            }
                        }
                    },
                    update = {
                        playerView = it
                        it.setPlayer(binding.player)
                    },
                    onRelease = { releasedView ->
                        releasedView.setControllerVisibilityListener(
                            null as PlayerView.ControllerVisibilityListener?,
                        )
                        releasedView.setPlayer(null)
                        if (playerView === releasedView) playerView = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                if (controlsVisible) {
                    VideoQuickControls(
                        instanceKey = instanceKey,
                        url = link.url,
                        binding = binding,
                        onKeepVisible = keepControlsVisible,
                        onInteractionChanged = controlsInteraction,
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    )
                }
            }

            fullscreen -> Text(
                text = "Playing fullscreen",
                color = ComposeColor.White,
                fontFamily = W95FA,
                fontSize = Win98Type.Body,
            )

            else -> Win98Button("▶ Play video") { binding.play(instanceKey, link.url) }
        }
    }
}

/** Owns the fullscreen surface while keeping the chat's single ExoPlayer instance. */
@Composable
fun FullscreenChatVideo(binding: InlineVideoBinding) {
    val player = binding.player ?: return
    val fullscreenKey = binding.fullscreenKey ?: return
    val controlsState = remember(fullscreenKey) { ChatVideoControlsState() }
    var controlsVisible by remember(fullscreenKey) { mutableStateOf(controlsState.visible) }
    var playerView by remember(fullscreenKey) { mutableStateOf<PlayerView?>(null) }
    val syncControls: (Boolean) -> Unit = { isVisible ->
        controlsState.controllerVisibilityChanged(isVisible)
        controlsVisible = controlsState.visible
    }
    val keepControlsVisible: () -> Unit = {
        playerView?.showController()
        syncControls(true)
    }
    val controlsInteraction: (Boolean) -> Unit = { activeInteraction ->
        controlsState.interactionChanged(activeInteraction)
        controlsVisible = controlsState.visible
        if (!activeInteraction) playerView?.showController()
    }

    Dialog(
        onDismissRequest = binding.exitFullscreen,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        FullscreenSystemBars()
        Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        playerView = this
                        setPlayer(player)
                        useController = true
                        controllerAutoShow = true
                        controllerShowTimeoutMs = ChatVideoControlsState.AUTO_HIDE_MILLIS
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                syncControls(visibility == View.VISIBLE)
                            },
                        )
                        showController()
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(Color.BLACK)
                        setFullscreenButtonState(true)
                        setFullscreenButtonClickListener { binding.exitFullscreen() }
                    }
                },
                update = {
                    playerView = it
                    it.setPlayer(player)
                },
                onRelease = { releasedView ->
                    releasedView.setControllerVisibilityListener(
                        null as PlayerView.ControllerVisibilityListener?,
                    )
                    releasedView.setPlayer(null)
                    if (playerView === releasedView) playerView = null
                },
                modifier = Modifier.fillMaxSize(),
            )
            val url = binding.activeUrl
            if (url != null && controlsVisible) {
                VideoQuickControls(
                    instanceKey = fullscreenKey,
                    url = url,
                    binding = binding,
                    onKeepVisible = keepControlsVisible,
                    onInteractionChanged = controlsInteraction,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoQuickControls(
    instanceKey: String,
    url: String,
    binding: InlineVideoBinding,
    onKeepVisible: () -> Unit,
    onInteractionChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showVolume by remember(instanceKey) { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Win98Button(
                if (showVolume) "Hide volume" else "Volume",
                modifier = Modifier.onFocusChanged { onInteractionChanged(it.isFocused) },
            ) {
                onKeepVisible()
                showVolume = !showVolume
            }
            Win98Button(
                "External",
                modifier = Modifier.onFocusChanged { onInteractionChanged(it.isFocused) },
            ) {
                onKeepVisible()
                binding.openExternal(instanceKey, url)
            }
        }
        if (showVolume) {
            Row(
                modifier = Modifier.background(Win98.Face).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Win98Button(
                    if (binding.volume <= 0f) "Unmute" else "Mute",
                    modifier = Modifier.onFocusChanged { onInteractionChanged(it.isFocused) },
                ) {
                    onKeepVisible()
                    binding.toggleMute()
                }
                VolumeMeterBar(
                    volume = binding.volume,
                    onVolume = binding.setVolume,
                    modifier = Modifier.width(140.dp),
                    onInteractionStart = {
                        onKeepVisible()
                        onInteractionChanged(true)
                    },
                    onInteractionEnd = { onInteractionChanged(false) },
                )
            }
        }
    }
}

@Composable
private fun FullscreenSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
