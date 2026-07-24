// SPDX-FileCopyrightText: 2026 Yumi Co. Radio
// SPDX-License-Identifier: GPL-3.0-or-later

package net.yumicoradio.android.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import net.yumicoradio.android.BuildConfig
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.yumicoradio.android.YumiApp
import net.yumicoradio.android.metadata.MetadataRepository
import net.yumicoradio.android.metadata.model.NowPlaying
import net.yumicoradio.android.ui.MainActivity

private const val ROOT_ID = "root"

class RadioPlaybackService : MediaLibraryService() {

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private lateinit var repo: MetadataRepository
    private val reconnect = ReconnectPolicy()
    private val handler = Handler(Looper.getMainLooper())
    private var attempt = 0

    private val sleep = SleepTimer(nowMs = { android.os.SystemClock.elapsedRealtime() })
    private val sleepScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val metaScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val sleepCommand = SessionCommand(CMD_SLEEP, Bundle.EMPTY)
    private val quitCommand = SessionCommand(CMD_QUIT, Bundle.EMPTY)

    /** Push resolved live title/artist/cover into the current item so notification + lockscreen + Auto show it. */
    private fun applyNowPlayingMetadata(np: NowPlaying) {
        val cur = player.currentMediaItem ?: return
        val meta = cur.mediaMetadata.buildUpon()
            .setStation("Yumi Co. Radio")
            .setTitle(np.title.ifBlank { "Yumi Co. Radio — Live" })
        if (np.artist.isNotBlank()) meta.setArtist(np.artist)
        if (!np.artworkUrl.isNullOrBlank()) meta.setArtworkUri(Uri.parse(np.artworkUrl))
        val updated = cur.buildUpon().setMediaMetadata(meta.build()).build()
        // Same URI → Media3 updates metadata in place, no re-buffer.
        player.replaceMediaItem(player.currentMediaItemIndex, updated)
    }

    /** minutes<=0 cancels. Media3 drops the foreground/notification once the player pauses. */
    fun startSleep(minutes: Int) {
        if (minutes <= 0) { sleep.cancel(); return }
        sleep.start(minutes * 60_000L) { player.pause() }
        sleepScope.launch {
            while (sleep.isActive) { sleep.tick(); delay(1000) }
        }
    }

    /**
     * What the stream server sees. Every part of this string is ours to choose — the platform
     * imposes nothing once a User-Agent is set explicitly.
     *
     * Product and version come first so the app is identifiable and so a problem can be pinned to a
     * release. The Android version and device model follow because they are what a playback report
     * turns on, and because they were already being sent in the default agent — this narrows what
     * leaves the device rather than widening it.
     */
    private fun userAgent(): String =
        "YumiCoRadio/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; ${Build.MODEL})"

    override fun onCreate() {
        super.onCreate()
        repo = (application as YumiApp).metadata
        repo.start()

        // A renderers factory whose audio sink carries the level tap. Overriding buildAudioSink is
        // the supported seam for this; the alternative — reading levels with the platform
        // Visualizer API — would drag RECORD_AUDIO into a radio player. See AudioLevels.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(AudioLevels.sink)))
                // Float output would bypass the 16-bit measurement and leave the meter dead.
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }

        // Identify the app to the stream server. Without this the request goes out with the
        // platform's default, `Dalvik/2.1.0 (Linux; U; Android 16; SM-...)`, which AzuraCast reads
        // as a generic "Android Browser" — the app's own listeners are indistinguishable from
        // someone opening the stream URL in Chrome.
        val http = DefaultHttpDataSource.Factory().setUserAgent(userAgent())

        player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)   // pause on headset unplug
            .build()

        player.addListener(object : Player.Listener {
            override fun onMetadata(metadata: Metadata) {
                for (i in 0 until metadata.length()) {
                    val e = metadata.get(i)
                    if (e is androidx.media3.extractor.metadata.icy.IcyInfo) {
                        repo.onIcyTitle(e.title)
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                val delay = reconnect.delayForAttempt(++attempt)
                handler.postDelayed({
                    player.prepare()      // re-buffer at live edge
                    player.play()
                }, delay)
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) attempt = 0
            }
        })

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(openApp)   // tap notification/lockscreen → reopen app
            .build()

        metaScope.launch {
            repo.nowPlaying.collect { np -> applyNowPlayingMetadata(np) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onDestroy() {
        repo.stop()
        sleepScope.cancel()
        metaScope.cancel()
        session.release()
        player.release()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // --- browse tree + media resolution + custom commands ---
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        // Advertise our custom sleep command so controllers are allowed to send it.
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val base = super.onConnect(session, controller)
            val commands = base.availableSessionCommands.buildUpon()
                .add(sleepCommand).add(quitCommand).build()
            return MediaSession.ConnectionResult.accept(commands, base.availablePlayerCommands)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_QUIT) {
                // Stop, drop the item so Media3 tears the notification down, then end the service.
                // finish() alone would leave both alive: playback deliberately outlives the UI.
                player.stop()
                player.clearMediaItems()
                stopSelf()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == CMD_SLEEP) {
                startSleep(args.getInt(KEY_SLEEP_MIN, 0))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // Resolve a requested media id (from UI or Android Auto) into a playable MediaItem with a URI.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { requested ->
                buildStreamItem(StreamQuality.fromMediaId(requested.mediaId))
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false)
                        .setTitle("Yumi Co. Radio").build()
                ).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId != ROOT_ID) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            val children = ImmutableList.copyOf(
                StreamQuality.entries.map { q -> browsableStreamNode(q.mediaId, "${q.kbps} kbps · Yumi Co. Radio") }
            )
            return Futures.immediateFuture(LibraryResult.ofItemList(children, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(buildStreamItem(StreamQuality.fromMediaId(mediaId)), null),
            )
        }
    }

    private fun browsableStreamNode(id: String, label: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(label).setStation("Yumi Co. Radio")
                    .setIsBrowsable(false).setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
                    .build()
            ).build()

    private fun buildStreamItem(q: StreamQuality): MediaItem =
        MediaItem.Builder()
            .setMediaId(q.mediaId)
            .setUri(q.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setStation("Yumi Co. Radio")
                    .setTitle("Yumi Co. Radio — Live")
                    .setIsBrowsable(false).setIsPlayable(true)
                    .build()
            )
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
            .build()

    companion object {
        const val ROOT = ROOT_ID
        const val CMD_SLEEP = "net.yumicoradio.SLEEP"
        const val CMD_QUIT = "net.yumicoradio.QUIT"
        const val KEY_SLEEP_MIN = "minutes"
    }
}
