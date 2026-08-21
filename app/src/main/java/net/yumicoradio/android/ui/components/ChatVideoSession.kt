// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import net.yumicoradio.android.chat.ChatVideoSessionState
import net.yumicoradio.android.chat.ChatVideoTarget
import net.yumicoradio.android.chat.ChatVideoVolumeState
import net.yumicoradio.android.ui.PlayerViewModel

/**
 * Owns the only ExoPlayer the chat is allowed to create.
 *
 * Inactive messages contain no decoder or surface. A user tap creates this player, and every exit
 * path funnels through [releaseIfActive] so audio focus, decoder resources and radio intent cannot
 * be stranded by scrolling, backgrounding or navigation.
 */
@Stable
class ChatVideoSession internal constructor(
    private val context: Context,
    private val pauseRadio: () -> Boolean,
    private val resumeRadio: () -> Unit,
    private val deferRadioResume: (Boolean) -> Unit,
    private val completeDeferredRadioResume: () -> Unit,
) {
    private val ownership = ChatVideoSessionState()
    private val volumeState = ChatVideoVolumeState()

    var activeKey by mutableStateOf<String?>(null)
        private set

    var player by mutableStateOf<Player?>(null)
        private set

    var errorKey by mutableStateOf<String?>(null)
        private set

    var fullscreenKey by mutableStateOf<String?>(null)
        private set

    var volume by mutableFloatStateOf(volumeState.volume)
        private set

    val activeUrl: String?
        get() = ownership.activeTarget?.url

    fun play(key: String, url: String) {
        errorKey = null
        val target = ChatVideoTarget(key = key, url = url)
        if (ownership.activeTarget == null) {
            ownership.start(target, radioWasPlaying = pauseRadio())
        } else {
            releasePlayerOnly()
            ownership.switchTo(target)
        }
        activeKey = key
        fullscreenKey = null

        val created = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setMediaItem(MediaItem.fromUri(url))
            volume = volumeState.volume
            playWhenReady = false
        }
        created.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && player === created) {
                    releaseIfActive(key)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (player === created) {
                    errorKey = key
                    releaseIfActive(key)
                }
            }
        })
        player = created
        created.prepare()
        created.play()
    }

    fun releaseIfActive(key: String) {
        if (ownership.activeTarget?.key != key) return
        releasePlayerOnly()
        activeKey = null
        fullscreenKey = null
        if (ownership.finish(key)) resumeRadio()
    }

    fun updateVisibility(key: String, visible: Boolean) {
        if (ownership.updateVisibility(key, visible)) releaseIfActive(key)
    }

    fun enterFullscreen(key: String) {
        if (ownership.enterFullscreen(key)) fullscreenKey = key
    }

    fun exitFullscreen() {
        val key = fullscreenKey
        val release = ownership.exitFullscreen()
        fullscreenKey = null
        if (release && key != null) releaseIfActive(key)
    }

    fun updateVolume(value: Float) {
        volume = volumeState.set(value)
        player?.volume = volume
    }

    fun toggleMute() {
        volume = volumeState.toggleMute()
        player?.volume = volume
    }

    fun releaseActive() {
        ownership.activeTarget?.key?.let(::releaseIfActive)
    }

    /** Releases inline playback and defers radio restoration until the external app returns. */
    fun openExternal(key: String, url: String, launch: (String) -> Boolean) {
        val resumeOnReturn = when {
            ownership.activeTarget?.key == key -> {
                releasePlayerOnly()
                activeKey = null
                fullscreenKey = null
                ownership.handoffToExternal(key)
            }
            errorKey == key -> pauseRadio()
            else -> false
        }
        deferRadioResume(resumeOnReturn)
        if (!launch(url)) completeExternalHandoff()
    }

    /** Called when this activity becomes active again after an external video player. */
    fun completeExternalHandoff() {
        completeDeferredRadioResume()
    }

    private fun releasePlayerOnly() {
        val current = player
        player = null
        current?.release()
    }
}

/** Remembers one session for the whole Chat screen, including its private-message windows. */
@Composable
fun rememberChatVideoSession(playerVm: PlayerViewModel): ChatVideoSession {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember(context, playerVm) {
        ChatVideoSession(
            context = context,
            pauseRadio = playerVm::pauseForChatVideo,
            resumeRadio = playerVm::resumeAfterChatVideo,
            deferRadioResume = playerVm::deferChatVideoRadioResume,
            completeDeferredRadioResume = playerVm::completeChatVideoExternalHandoff,
        )
    }

    DisposableEffect(session, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) session.releaseActive()
            if (event == Lifecycle.Event.ON_RESUME) session.completeExternalHandoff()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            session.releaseActive()
        }
    }
    return session
}
