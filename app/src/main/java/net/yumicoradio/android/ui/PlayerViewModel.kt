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
import net.yumicoradio.android.BuildConfig
import net.yumicoradio.android.chat.ChatVideoExternalHandoffState
import net.yumicoradio.android.playback.Equalizer
import net.yumicoradio.android.playback.EqualizerSpec
import net.yumicoradio.android.playback.RadioPlaybackService
import net.yumicoradio.android.playback.StreamQuality
import net.yumicoradio.android.metadata.model.NowPlaying
import net.yumicoradio.android.metadata.model.RecentTrack
import net.yumicoradio.android.update.FdroidUpdateChecker
import net.yumicoradio.android.update.FdroidUpdateResult
import net.yumicoradio.android.update.UpdatePolicy
import net.yumicoradio.android.update.UpdateState

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val yumi = app as YumiApp
    private var controller: MediaController? = null
    private var binding = false
    private val updateChecker = FdroidUpdateChecker(yumi.http)
    private var updateCheckRunning = false
    private val chatVideoExternalHandoff = ChatVideoExternalHandoffState()

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
    val automaticUpdateChecks: StateFlow<Boolean> =
        yumi.prefs.automaticUpdateChecks.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val eqEnabled: StateFlow<Boolean> =
        yumi.prefs.eqEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _eqGains = MutableStateFlow(EqualizerSpec.ZERO_GAINS)
    val eqGains: StateFlow<List<Int>> = _eqGains.asStateFlow()

    /** The preset name matching the current gains, or null once they are hand-tweaked. */
    val eqPreset: StateFlow<String?> = _eqGains
        .map { EqualizerSpec.presetFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, EqualizerSpec.FLAT)

    init {
        // Keep the meter in sync with the persisted level (and any later external change).
        viewModelScope.launch { yumi.prefs.volume.collect { _volume.value = it } }
        // Push persisted equaliser settings into the audio-thread singleton and mirror them for the UI.
        viewModelScope.launch { yumi.prefs.eqGains.collect { _eqGains.value = it; Equalizer.setGains(it) } }
        viewModelScope.launch { yumi.prefs.eqEnabled.collect { Equalizer.setEnabled(it) } }
        // DataStore first emits the default false, then the persisted opt-in. Only the latter may
        // contact F-Droid, and the policy enforces the daily ceiling before any request is made.
        viewModelScope.launch {
            automaticUpdateChecks.collect { enabled ->
                if (enabled) checkForUpdates(manual = false)
            }
        }
    }

    fun setAutomaticUpdateChecks(enabled: Boolean) {
        viewModelScope.launch { yumi.prefs.setAutomaticUpdateChecks(enabled) }
    }

    fun checkForUpdates(manual: Boolean = true) {
        if (updateCheckRunning) return
        updateCheckRunning = true
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                if (!manual && !UpdatePolicy.shouldCheckAutomatically(
                        enabled = automaticUpdateChecks.value,
                        now = now,
                        lastAttempt = yumi.prefs.lastUpdateAttempt(),
                    )
                ) return@launch

                if (manual) _updateState.value = UpdateState.Checking
                yumi.prefs.setLastUpdateAttempt(now)
                when (val result = updateChecker.check(BuildConfig.VERSION_CODE)) {
                    is FdroidUpdateResult.Available -> {
                        val show = manual || UpdatePolicy.shouldShowAutomatically(
                            result.versionCode,
                            yumi.prefs.dismissedUpdateCode(),
                        )
                        _updateState.value = if (show) {
                            UpdateState.Available(result.versionCode)
                        } else {
                            UpdateState.Idle
                        }
                    }
                    FdroidUpdateResult.UpToDate -> {
                        _updateState.value = if (manual) UpdateState.UpToDate else UpdateState.Idle
                    }
                    is FdroidUpdateResult.Failure -> {
                        _updateState.value = if (manual) {
                            UpdateState.Error(result.message)
                        } else {
                            UpdateState.Idle
                        }
                    }
                }
            } finally {
                updateCheckRunning = false
            }
        }
    }

    fun dismissUpdateState() {
        val current = _updateState.value
        _updateState.value = UpdateState.Idle
        if (current is UpdateState.Available) {
            viewModelScope.launch { yumi.prefs.setDismissedUpdateCode(current.versionCode) }
        }
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

    /**
     * Gives a short in-app video the radio's place without losing the user's original intent.
     * The returned flag belongs to that video dialog and is the only reason it may resume later.
     */
    fun pauseForChatVideo(): Boolean {
        val wasPlaying = controller?.isPlaying == true
        if (wasPlaying) controller?.pause()
        return wasPlaying
    }

    /** Reclaims audio focus after a chat video only when [pauseForChatVideo] returned true. */
    fun resumeAfterChatVideo() {
        val c = controller ?: return
        if (c.currentMediaItem == null) setQualityItem(quality.value)
        c.prepare()
        c.play()
    }

    /** Retained by this ViewModel if Android recreates the Activity while another player is open. */
    fun deferChatVideoRadioResume(shouldResumeRadio: Boolean) {
        chatVideoExternalHandoff.defer(shouldResumeRadio)
    }

    fun completeChatVideoExternalHandoff() {
        if (chatVideoExternalHandoff.consumeResumeIntent()) resumeAfterChatVideo()
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { yumi.prefs.setDarkMode(enabled) }
    }

    fun setEqEnabled(on: Boolean) {
        Equalizer.setEnabled(on)
        viewModelScope.launch { yumi.prefs.setEqEnabled(on) }
    }

    /** Drag on one band. Updates the live filter at once; persists in the background. */
    fun setEqBand(index: Int, gainDb: Int) {
        val next = _eqGains.value.toMutableList().also {
            it[index] = gainDb.coerceIn(EqualizerSpec.MIN_DB, EqualizerSpec.MAX_DB)
        }
        _eqGains.value = next
        Equalizer.setGains(next)
        viewModelScope.launch { yumi.prefs.setEqGains(next) }
    }

    fun selectEqPreset(name: String) {
        val gains = EqualizerSpec.PRESETS[name] ?: return
        _eqGains.value = gains
        Equalizer.setGains(gains)
        viewModelScope.launch { yumi.prefs.setEqGains(gains) }
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
