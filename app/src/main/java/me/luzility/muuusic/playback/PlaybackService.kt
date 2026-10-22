package me.luzility.muuusic.playback

import me.luzility.muuusic.R
import me.luzility.muuusic.data.download.DownloadStatus
import android.widget.Toast
import me.luzility.muuusic.playback.audio.Karaoke
import me.luzility.muuusic.playback.audio.KaraokeAudioProcessor
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.data.model.SongJson
import org.json.JSONObject
import androidx.media3.common.PlaybackParameters
import me.luzility.muuusic.data.settings.AppPrefs
import kotlin.math.pow
import me.luzility.muuusic.playback.audio.GainAudioProcessor
import me.luzility.muuusic.playback.audio.AudioEffects
import me.luzility.muuusic.playback.audio.Crossfader
import me.luzility.muuusic.data.sponsorblock.Segment
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
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.SettableFuture
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import android.os.Bundle
import android.graphics.Bitmap
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import me.luzility.muuusic.widget.NowPlayingWidget
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class PlaybackService : MediaLibraryService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaLibrarySession? = null
    private lateinit var browseTree: BrowseTree
    private lateinit var player: ExoPlayer

    private var radioJob: Job? = null
    private var retryId: String? = null
    private var retryCount = 0
    private var failedInRow = 0
    private var recordedFor: String? = null
    private var restoreDone = false

    private lateinit var queueStore: QueueStore
    private var saveJob: Job? = null
    private var positionJob: Job? = null
    private val playbackWanted = MutableStateFlow(false)
    private var waitingForNetwork = false
    // Restoring the saved queue is not a new play.
    private var restoring = false

    private var sleepJob: Job? = null

    private var gain = GainAudioProcessor()
    private val listener = PlayerListener()
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private lateinit var effects: AudioEffects
    private lateinit var crossfader: Crossfader
    private var normalizeJob: Job? = null

    private var segments: List<Segment> = emptyList()
    private var segmentsFor: String? = null
    private var segmentJob: Job? = null

    private var widgetJob: Job? = null
    /** Song whose artwork is saved for the widget. */
    private var widgetArtFor: String? = null

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
                player.playbackParameters = playbackParametersOf(prefs)
                Karaoke.setStrength(prefs.karaokeStrength)
                if (!prefs.karaoke) Karaoke.set(false)
                normalize(player.currentMediaItem?.mediaId)
                val current = player.currentMediaItem?.mediaId
                if (prefs.sponsorBlock && current != null && current != segmentsFor) loadSegments(current)
            }
        }
        scope.launch {
            container.connectivity.online.collect { online ->
                if (online && waitingForNetwork) {
                    waitingForNetwork = false
                    player.prepare()
                    player.play()
                }
            }
        }
        scope.launch {
            while (true) {
                crossfader.tick(container.settings.prefs.value.fadeSeconds, player, gain)
                skipSegment()
                if (player.isPlaying) delay(FADE_TICK_MS) else playbackWanted.first { it }
            }
        }
        queueStore = QueueStore(File(filesDir, "queue.json"))
        restoreQueue()

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        browseTree = BrowseTree(this)
        session = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing in the background when the app is swiped away; otherwise shut down.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Last chance to save the queue; the file is tiny so write inline.
        saveJob?.cancel()
        if (restoreDone) snapshot()?.let(queueStore::save) ?: queueStore.clear()
        val item = player.currentMediaItem
        val artKey = widgetArtFor?.takeIf { it == item?.mediaId }
        val app = applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            NowPlayingWidget.update(app, item?.mediaMetadata?.title?.toString(), item?.mediaMetadata?.artist?.toString(), false, artKey, null)
        }
        scope.cancel()
        crossfader.release()
        effects.release()
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(
                    MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                        .add(SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY))
                        .add(SessionCommand(QueueCommands.ENQUEUE, Bundle.EMPTY))
                        .add(SessionCommand(QueueCommands.START_RADIO, Bundle.EMPTY))
                        .build()
                )
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                SleepTimer.COMMAND -> setSleepTimer(args.getInt(SleepTimer.ARG_MINUTES))
                QueueCommands.ENQUEUE -> {
                    val song = songFrom(args) ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    enqueueShuffled(song, args.getBoolean(QueueCommands.ARG_NEXT))
                }
                QueueCommands.START_RADIO -> {
                    val song = songFrom(args) ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
                    fetchRadio(song.id)
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        private fun songFrom(args: Bundle): Song? =
            args.getString(QueueCommands.ARG_SONG)?.let { json -> runCatching { SongJson.fromJson(JSONObject(json)) }.getOrNull() }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = future {
            mediaItems.map { item ->
                browseTree.resolve(item.mediaId)?.let { (songs, index) -> songs[index].toMediaItem() }
                    ?: MediaItems.withPlaybackUri(item)
            }.toMutableList()
        }

        // A song picked in Android Auto plays its whole list from there; a voice request plays search results.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = future {
            val single = mediaItems.singleOrNull()
            val picked = single?.let { browseTree.resolve(it.mediaId) }
            val query = single?.requestMetadata?.searchQuery
            when {
                picked != null -> MediaSession.MediaItemsWithStartPosition(
                    picked.first.map { it.toMediaItem() }, picked.second, startPositionMs,
                )
                single != null && single.mediaId.isEmpty() && !query.isNullOrBlank() -> MediaSession.MediaItemsWithStartPosition(
                    browseTree.searchSongs(query).map { it.toMediaItem() }, 0, C.TIME_UNSET,
                )
                else -> MediaSession.MediaItemsWithStartPosition(
                    mediaItems.map(MediaItems::withPlaybackUri), startIndex, startPositionMs,
                )
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browseTree.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(browseTree.children(parentId).page(page, pageSize), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = future {
            browseTree.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = future {
            session.notifySearchResultChanged(browser, query, browseTree.search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = future {
            LibraryResult.ofItemList(browseTree.search(query).page(page, pageSize), params)
        }

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
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                failedInRow = 0
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) clearSleepTimer()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            playbackWanted.value = this@PlaybackService.player.isPlaying
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || !this@PlaybackService.player.playWhenReady) waitingForNetwork = false
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                updateWidget()
            }
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
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                trackPosition(player.isPlaying)
                if (player.isPlaying) onPlaybackStarted()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val id = player.currentMediaItem?.mediaId ?: return
            val httpError = generateSequence(error as Throwable) { it.cause }
                .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
                .firstOrNull()

            if (!container.connectivity.online.value) {
                skipToDownloaded()
                return
            }

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
            failedInRow++
            if (failedInRow < MAX_SKIPS_IN_ROW && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val result = SettableFuture.create<T>()
        scope.launch {
            try {
                result.set(block())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Library request failed", e)
                result.setException(e)
            }
        }
        return result
    }

    private fun List<MediaItem>.page(page: Int, pageSize: Int): List<MediaItem> {
        if (pageSize <= 0 || page < 0) return this
        val from = page * pageSize
        return if (from >= size) emptyList() else subList(from, minOf(size, from + pageSize))
    }

    private fun playbackParametersOf(prefs: AppPrefs): PlaybackParameters {
        val pitch = if (prefs.linkPitch) prefs.playbackSpeed else 2.0.pow(prefs.pitchSemitones / 12.0).toFloat()
        return PlaybackParameters(prefs.playbackSpeed, pitch)
    }

    private fun onSongStarted(mediaItem: MediaItem) {
        normalize(mediaItem.mediaId)
        loadSegments(mediaItem.mediaId)
        recordedFor = null
        if (restoring) return
        if (player.isPlaying) onPlaybackStarted()
        extendQueueIfNeeded()
    }

    private fun onPlaybackStarted() {
        val item = player.currentMediaItem ?: return
        if (recordedFor != item.mediaId) {
            recordedFor = item.mediaId
            val song = item.toSong()
            scope.launch(Dispatchers.IO) { container.library.recordPlay(song) }
        }
        extendQueueIfNeeded()
    }

    private fun skipToDownloaded() {
        val downloaded = container.downloads.statuses.value
        val order = playOrder()
        val next = order.drop(order.indexOf(player.currentMediaItemIndex) + 1)
            .firstOrNull { downloaded[player.getMediaItemAt(it).mediaId]?.state == DownloadStatus.State.Done }
        Toast.makeText(this, R.string.offline_playback, Toast.LENGTH_SHORT).show()
        if (next != null) {
            player.seekTo(next, 0)
            player.prepare()
            player.play()
        } else {
            waitingForNetwork = true
        }
    }

    private fun playOrder(): MutableList<Int> {
        val timeline = player.currentTimeline
        val order = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(player.shuffleModeEnabled)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        }
        return order
    }

    private fun enqueueShuffled(song: Song, next: Boolean) {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(song.toMediaItem())
            player.prepare()
            player.play()
            return
        }
        val current = player.currentMediaItemIndex
        if (player.currentMediaItem?.mediaId == song.id) return
        val index = (0 until player.mediaItemCount).firstOrNull { it != current && player.getMediaItemAt(it).mediaId == song.id }
            ?: player.mediaItemCount.also { player.addMediaItem(song.toMediaItem()) }
        val order = playOrder()
        order.remove(index)
        val at = if (next) order.indexOf(player.currentMediaItemIndex) + 1 else order.size
        order.add(at.coerceIn(0, order.size), index)
        if (order.size == player.mediaItemCount) {
            player.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), Random.nextLong()))
        }
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

    private fun updateWidget() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(WIDGET_DEBOUNCE_MS)
            val item = player.currentMediaItem
            val id = item?.mediaId
            val meta = item?.mediaMetadata
            var art: Bitmap? = null
            if (id != null && id != widgetArtFor) {
                art = meta?.artworkUri?.let { loadArtwork(it) }
                widgetArtFor = if (art != null) id else null
            }
            NowPlayingWidget.update(
                this@PlaybackService,
                meta?.title?.toString(),
                meta?.artist?.toString(),
                player.playWhenReady,
                widgetArtFor?.takeIf { it == id },
                art,
            )
        }
    }

    private suspend fun loadArtwork(uri: Uri): Bitmap? = runCatching {
        val request = ImageRequest.Builder(this).data(uri).size(WIDGET_ART_PX).allowHardware(false).build()
        SingletonImageLoader.get(this).execute(request).image?.toBitmap()
    }.getOrNull()

    private fun loadSegments(videoId: String) {
        segmentJob?.cancel()
        segments = emptyList()
        segmentsFor = null
        if (!container.settings.prefs.value.sponsorBlock) return
        segmentsFor = videoId
        val next = if (player.hasNextMediaItem()) player.getMediaItemAt(player.nextMediaItemIndex).mediaId else null
        segmentJob = scope.launch {
            val found = runCatching { container.sponsorBlock.segments(videoId) }.getOrDefault(emptyList())
            if (player.currentMediaItem?.mediaId == videoId) segments = found
            if (next != null) runCatching { container.sponsorBlock.segments(next) }
        }
    }

    private fun skipSegment() {
        if (segments.isEmpty() || !player.isPlaying || !container.settings.prefs.value.sponsorBlock) return
        val position = player.currentPosition
        // Stop a little before the end so a segment just skipped doesn't match again.
        val segment = segments.firstOrNull { position >= it.startMs && position < it.endMs - 500 } ?: return
        val duration = player.duration
        if (duration != C.TIME_UNSET && segment.endMs >= duration - 1_000) {
            if (player.hasNextMediaItem()) player.seekToNextMediaItem() else player.seekTo(duration)
        } else {
            player.seekTo(segment.endMs)
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
                .setAudioProcessors(arrayOf(KaraokeAudioProcessor(), gain))
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
            playbackParameters = old.playbackParameters
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
        updateWidget()
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
            val saved = withContext(Dispatchers.IO) { queueStore.load() }
            restoreDone = true
            saved ?: return@launch
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
        val order = playOrder()
        val remaining = order.size - order.indexOf(player.currentMediaItemIndex) - 1
        if (remaining > 1 || radioJob?.isActive == true) return
        fetchRadio(player.currentMediaItem?.mediaId ?: return)
    }

    private fun fetchRadio(seedId: String) {
        radioJob?.cancel()
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
        const val MAX_SKIPS_IN_ROW = 3
        const val SAVE_DEBOUNCE_MS = 500L
        const val POSITION_SAVE_INTERVAL_MS = 10_000L
        const val FADE_TICK_MS = 50L
        const val WIDGET_DEBOUNCE_MS = 300L
        const val WIDGET_ART_PX = 300
        val musicAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        const val MIN_GAIN_DB = -15f
        const val MAX_GAIN_DB = 6f
    }
}
