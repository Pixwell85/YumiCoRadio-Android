// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.playback.RadioPlaybackService
import net.yumicoradio.android.playback.StreamQuality
import net.yumicoradio.android.metadata.model.NowPlaying
import net.yumicoradio.android.metadata.model.RecentTrack

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val yumi = app as YumiApp
    private var controller: MediaController? = null
    private var binding = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    val nowPlaying: StateFlow<NowPlaying> = yumi.metadata.nowPlaying
    val recent: StateFlow<List<RecentTrack>> = yumi.metadata.recent
    val quality: StateFlow<StreamQuality> =
        yumi.prefs.quality.stateIn(viewModelScope, SharingStarted.Eagerly, StreamQuality.DEFAULT)
    val darkMode: StateFlow<Boolean> =
        yumi.prefs.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Keep the meter in sync with the persisted level (and any later external change).
        viewModelScope.launch { yumi.prefs.volume.collect { _volume.value = it } }
    }

    fun bind() {
        // The ViewModel outlives the Activity, but `bind()` is driven from a LaunchedEffect(Unit)
        // that re-runs on every recreation (rotation, config change). Without this guard each
        // recreation would build another MediaController and strand the previous one — onCleared
        // releases only the last. Cover the in-flight window too: two buildAsync() calls before the
        // first completes would both assign, leaking one.
        if (controller != null || binding) return
        binding = true
        val token = SessionToken(getApplication(), ComponentName(getApplication(), RadioPlaybackService::class.java))
        val future = MediaController.Builder(getApplication(), token).buildAsync()
        future.addListener({
            binding = false
            controller = future.get().also { c ->
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
                    override fun onVolumeChanged(v: Float) { _volume.value = v }
                })
                _isPlaying.value = c.isPlaying
                c.volume = _volume.value          // apply the persisted volume to the session
            }
        }, MoreExecutors.directExecutor())
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause()
        else {
            if (c.currentMediaItem == null) setQualityItem(quality.value)
            c.prepare(); c.play()
        }
    }

    fun setVolume(v: Float) {
        val clamped = v.coerceIn(0f, 1f)
        controller?.volume = clamped
        _volume.value = clamped
        viewModelScope.launch { yumi.prefs.setVolume(clamped) }
    }

    /** Stop button: pause playback but keep the session/notification (unlike quit()). */
    fun stop() { controller?.pause() }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { yumi.prefs.setDarkMode(enabled) }
    }

    fun setQuality(q: StreamQuality) {
        viewModelScope.launch { yumi.prefs.setQuality(q) }
        val wasPlaying = controller?.isPlaying == true
        setQualityItem(q)
        if (wasPlaying) { controller?.prepare(); controller?.play() }
    }

    /** Stops playback and ends the service, so no notification survives the window closing. */
    fun quit() {
        controller?.sendCustomCommand(
            SessionCommand(RadioPlaybackService.CMD_QUIT, Bundle.EMPTY), Bundle.EMPTY,
        )
    }

    fun startSleep(minutes: Int) {
        val c = controller ?: return
        val args = Bundle().apply { putInt(RadioPlaybackService.KEY_SLEEP_MIN, minutes) }
        c.sendCustomCommand(SessionCommand(RadioPlaybackService.CMD_SLEEP, Bundle.EMPTY), args)
    }

    private fun setQualityItem(q: StreamQuality) {
        controller?.setMediaItem(MediaItem.Builder().setMediaId(q.mediaId).build())
    }

    override fun onCleared() { controller?.release(); controller = null }
}
