package me.luzility.muuusic.playback

import kotlin.math.pow
import me.luzility.muuusic.playback.audio.GainAudioProcessor
import me.luzility.muuusic.playback.audio.AudioEffects
import me.luzility.muuusic.playback.audio.Crossfader
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.common.util.Util
import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import android.os.Bundle
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import me.luzility.muuusic.MainActivity
import me.luzility.muuusic.container
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer

    private var radioJob: Job? = null
    private var retryId: String? = null
    private var retryCount = 0

    private lateinit var queueStore: QueueStore
    private var saveJob: Job? = null
    private var positionJob: Job? = null
    // Restoring the saved queue is not a new play.
    private var restoring = false

    private var sleepJob: Job? = null

    private var gain = GainAudioProcessor()
    private val listener = PlayerListener()
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private lateinit var effects: AudioEffects
    private lateinit var crossfader: Crossfader
    private var normalizeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // A fixed audio session lets the equalizer attach before anything plays, and is shared
        // by the players a crossfade creates.
        audioSessionId = Util.generateAudioSessionIdV21(this)
        player = buildPlayer(gain)
        player.addListener(listener)
        effects = AudioEffects(audioSessionId)
        container.equalizerLayout.value = effects.layout
        crossfader = Crossfader(::crossfadeToNext)
        scope.launch {
            container.settings.prefs.collect { prefs ->
                effects.apply(prefs)
                normalize(player.currentMediaItem?.mediaId)
            }
        }
        scope.launch {
            while (true) {
                crossfader.tick(container.settings.prefs.value.fadeSeconds, player, gain)
                delay(FADE_TICK_MS)
            }
        }
        queueStore = QueueStore(File(filesDir, "queue.json"))
        restoreQueue()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in the background when the app is swiped away; otherwise shut down.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Last chance to save the queue; the file is tiny so write inline.
        snapshot()?.let(queueStore::save) ?: queueStore.clear()
        scope.cancel()
        crossfader.release()
        effects.release()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY))
                        .build()
                )
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SleepTimer.COMMAND) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            setSleepTimer(args.getInt(SleepTimer.ARG_MINUTES))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map(MediaItems::withPlaybackUri).toMutableList())

        // Play pressed on headphones or system controls while the app wasn't running.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = queueStore.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    saved.songs.map { it.toMediaItem() },
                    saved.index,
                    saved.positionMs,
                )
            )
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            onSongStarted(mediaItem ?: return)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) retryCount = 0
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) clearSleepTimer()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (restoring) return
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                scheduleSave()
            }
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) trackPosition(player.isPlaying)
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
            val httpError = generateSequence(error as Throwable) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()

            // Stream URLs expire or get rejected now and then; resolve a fresh one a couple of
            // times before giving up on the track.
            if (retryId != id) {
                retryId = id
                retryCount = 0
            }
            if (httpError != null && httpError.responseCode == 403 && retryCount < MAX_RETRIES) {
                retryCount++
                container.youtube.invalidateStreamUrl(id)
                player.prepare()
                player.play()
                return
            }
            Log.w(TAG, "Playback failed for $id", error)
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun onSongStarted(mediaItem: MediaItem) {
        normalize(mediaItem.mediaId)
        if (restoring) return
        val song = mediaItem.toSong()
        scope.launch(Dispatchers.IO) { container.library.recordPlay(song) }
        extendQueueIfNeeded()
    }

    // Fades out over the last few seconds. The end time goes out via session extras for the UI countdown.
    private fun setSleepTimer(minutes: Int) {
        clearSleepTimer()
        when {
            minutes == SleepTimer.END_OF_SONG -> {
                player.pauseAtEndOfMediaItems = true
                publishSleepTimer(SleepTimer.ENDS_WITH_SONG)
            }
            minutes > 0 -> {
                val endsAt = System.currentTimeMillis() + minutes * 60_000L
                publishSleepTimer(endsAt)
                sleepJob = scope.launch {
                    delay((endsAt - System.currentTimeMillis() - SleepTimer.FADE_MS).coerceAtLeast(0))
                    val steps = 40
                    repeat(steps) { step ->
                        player.volume = 1f - (step + 1f) / steps
                        delay(SleepTimer.FADE_MS / steps)
                    }
                    player.pause()
                    clearSleepTimer()
                }
            }
        }
    }

    private fun clearSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        player.pauseAtEndOfMediaItems = false
        player.volume = 1f
        publishSleepTimer(0)
    }

    private fun publishSleepTimer(endsAt: Long) {
        session?.setSessionExtras(Bundle().apply { putLong(SleepTimer.EXTRA_ENDS_AT, endsAt) })
    }

    // Gain is looked up once per song, and the next song's ahead of time.
    private fun normalize(videoId: String?) {
        normalizeJob?.cancel()
        val gain = gain
        if (videoId == null || !container.settings.prefs.value.normalizeVolume) {
            gain.normalization = 1f
            return
        }
        val known = container.loudness.get(videoId)
        gain.normalization = known?.let(::dbToLinear) ?: 1f
        val next = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex).mediaId else null
        normalizeJob = scope.launch {
            if (known == null) {
                lookupLoudness(videoId)?.let { db ->
                    if (player.currentMediaItem?.mediaId == videoId) gain.normalization = dbToLinear(db)
                }
            }
            if (next != null && container.loudness.get(next) == null) lookupLoudness(next)
        }
    }

    private suspend fun lookupLoudness(videoId: String): Float? =
        runCatching { container.innerTube.loudnessGainDb(videoId) }.getOrNull()?.toFloat()
            ?.also { container.loudness.put(videoId, it) }

    // Capped: boosting very quiet tracks mostly boosts their noise.
    private fun dbToLinear(db: Float): Float =
        10.0.pow(db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) / 20.0).toFloat()

    // The gain stage sits in the audio sink, after decoding.
    private fun renderersWith(gain: GainAudioProcessor) = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(gain))
                .build()
    }

    private fun buildPlayer(gain: GainAudioProcessor): ExoPlayer =
        ExoPlayer.Builder(this, renderersWith(gain))
            .setMediaSourceFactory(DefaultMediaSourceFactory(container.playerDataSourceFactory()))
            .setAudioAttributes(musicAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { it.audioSessionId = audioSessionId }

    // The new player gets a copy of the queue and takes the session; the old one only plays out
    // its ending.
    private fun crossfadeToNext(): Crossfader.Outgoing? {
        val old = player
        val oldGain = gain
        val nextIndex = old.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return null
        val newGain = GainAudioProcessor().apply { fade = 0f }
        val next = buildPlayer(newGain).apply {
            setMediaItems((0 until old.mediaItemCount).map(old::getMediaItemAt), nextIndex, 0)
            setShuffleOrder(shuffleOrderOf(old))
            shuffleModeEnabled = old.shuffleModeEnabled
            repeatMode = old.repeatMode
            volume = old.volume
        }

        // The old player gives up the session and audio focus, and stops once its song ends.
        old.removeListener(listener)
        old.setAudioAttributes(musicAttributes, /* handleAudioFocus = */ false)
        old.setHandleAudioBecomingNoisy(false)
        old.pauseAtEndOfMediaItems = true

        player = next
        gain = newGain
        next.addListener(listener)
        session?.player = next
        next.prepare()
        next.play()
        next.currentMediaItem?.let(::onSongStarted)
        scheduleSave()
        return Crossfader.Outgoing(old, oldGain, fadeStartMs = old.currentPosition)
    }

    // Shuffle order, so the new player continues it.
    private fun shuffleOrderOf(player: ExoPlayer): ShuffleOrder {
        val timeline = player.currentTimeline
        val order = IntArray(timeline.windowCount)
        var index = timeline.getFirstWindowIndex(true)
        var count = 0
        while (index != C.INDEX_UNSET && count < order.size) {
            order[count++] = index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        return if (count == order.size) {
            ShuffleOrder.DefaultShuffleOrder(order, Random.nextLong())
        } else {
            ShuffleOrder.DefaultShuffleOrder(order.size)
        }
    }

    // Streams only load once play is pressed.
    private fun restoreQueue() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { queueStore.load() } ?: return@launch
            // Something may already have started playing while the file was being read.
            if (player.mediaItemCount > 0) return@launch
            restoring = true
            try {
                player.shuffleModeEnabled = saved.shuffle
                player.repeatMode = saved.repeatMode
                player.setMediaItems(saved.songs.map { it.toMediaItem() }, saved.index, saved.positionMs)
            } finally {
                restoring = false
            }
        }
    }

    private fun snapshot(): QueueSnapshot? {
        if (player.mediaItemCount == 0) return null
        return QueueSnapshot(
            songs = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() },
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    // Coalesces bursts of player events into one write.
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val snapshot = snapshot()
            withContext(Dispatchers.IO) { snapshot?.let(queueStore::save) ?: queueStore.clear() }
        }
    }

    // Position changes without events while playing.
    private fun trackPosition(playing: Boolean) {
        positionJob?.cancel()
        if (!playing) return
        positionJob = scope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                scheduleSave()
            }
        }
    }

    private fun extendQueueIfNeeded() {
        if (!container.settings.prefs.value.autoplay) return
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining > 1 || radioJob?.isActive == true) return
        val seedId = player.currentMediaItem?.mediaId ?: return

        radioJob = scope.launch {
            val songs = runCatching { container.youtube.radio(seedId) }
                .onFailure { Log.w(TAG, "Radio failed for $seedId", it) }
                .getOrNull() ?: return@launch
            val queued = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val fresh = songs.filter { it.id !in queued }.take(25)
            if (fresh.isNotEmpty()) player.addMediaItems(fresh.map { it.toMediaItem() })
        }
    }

    private companion object {
        const val TAG = "PlaybackService"
        const val MAX_RETRIES = 2
        const val SAVE_DEBOUNCE_MS = 500L
        const val POSITION_SAVE_INTERVAL_MS = 10_000L
        const val FADE_TICK_MS = 50L
        val musicAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        const val MIN_GAIN_DB = -15f
        const val MAX_GAIN_DB = 6f
    }
}
