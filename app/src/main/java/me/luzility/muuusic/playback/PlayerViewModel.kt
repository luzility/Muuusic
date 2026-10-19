package me.luzility.muuusic.playback

import android.app.Application
import android.content.ComponentName
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import me.luzility.muuusic.container
import me.luzility.muuusic.data.lyrics.Lyrics
import me.luzility.muuusic.data.lyrics.LyricsPrefs
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.ui.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PlayerUiState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val library = app.container.library
    private val _sleepEndsAt = MutableStateFlow(0L)

    // 0 = off, SleepTimer.ENDS_WITH_SONG, or the epoch millis when it fires.
    val sleepEndsAt: StateFlow<Long> = _sleepEndsAt.asStateFlow()

    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java)))
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    _sleepEndsAt.value = extras.getLong(SleepTimer.EXTRA_ENDS_AT)
                }
            })
            .buildAsync()
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLiked: StateFlow<Boolean> = _state
        .map { it.current?.id }
        .flatMapLatest { id -> if (id == null) flowOf(false) else library.isLiked(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val lyricsRepository = app.container.lyrics
    private val _showLyrics = MutableStateFlow(false)
    private val lyricsRetry = MutableStateFlow(0)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    // Only fetched while the lyrics panel is open.
    @OptIn(ExperimentalCoroutinesApi::class)
    val lyrics: StateFlow<UiState<Lyrics?>> = combine(
        _state.map { it.current to it.durationMs }.distinctUntilChangedBy { it.first?.id },
        _showLyrics,
        lyricsRetry,
    ) { current, show, _ -> if (show) current else null }
        .filterNotNull()
        .transformLatest { (song, durationMs) ->
            if (song == null) return@transformLatest
            val cached = lyricsRepository.cached(song)
            if (cached != null) {
                emit(UiState.Success(cached.takeUnless(Lyrics::isEmpty)))
            } else {
                emit(UiState.Loading)
                val durationSec = song.durationSec.takeIf { it > 0 } ?: (durationMs / 1000)
                emit(UiState.of { lyricsRepository.lyricsFor(song, durationSec) })
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val lyricsSettings = app.container.lyricsSettings
    val lyricsPrefs: StateFlow<LyricsPrefs> = lyricsSettings.prefs

    @OptIn(ExperimentalCoroutinesApi::class)
    val lyricsOffset: StateFlow<Long> = _state
        .map { it.current?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(0L) else lyricsSettings.offsetFor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setLyricsOffset(offsetMs: Long) {
        _state.value.current?.let { lyricsSettings.setOffset(it.id, offsetMs) }
    }

    fun updateLyricsPrefs(transform: (LyricsPrefs) -> LyricsPrefs) = lyricsSettings.update(transform)

    fun retryLyrics() {
        lyricsRetry.value++
    }

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        controllerFuture.addListener({
            val c = controllerFuture.get()
            _sleepEndsAt.value = c.sessionExtras.getLong(SleepTimer.EXTRA_ENDS_AT)
            controller = c
            c.addListener(listener)
            sync(c)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(player: Player) {
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() }
        val current = player.currentMediaItem?.toSong()
        _state.value = PlayerUiState(
            current = current,
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            // A restored queue isn't loaded until play is pressed; use the saved length until then.
            durationMs = player.duration.takeIf { it > 0 } ?: ((current?.durationSec ?: 0) * 1000),
            queue = queue,
            currentIndex = player.currentMediaItemIndex,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
    }

    fun position(): Long = controller?.currentPosition ?: 0

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(songs.map { it.toMediaItem() }, if (shuffle) songs.indices.random() else startIndex, 0)
        c.prepare()
        c.play()
    }

    fun startRadio(song: Song) = play(listOf(song))

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(song))
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return play(listOf(song))
        c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.isPlaying) c.pause() else c.play()
    }

    fun setSleepTimer(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand(SleepTimer.COMMAND, Bundle.EMPTY),
            Bundle().apply { putInt(SleepTimer.ARG_MINUTES, minutes) },
        )
    }

    fun dismiss() {
        controller?.let {
            it.stop()
            it.clearMediaItems()
        }
    }

    fun pause() = controller?.pause()

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun skipTo(index: Int) = controller?.seekTo(index, 0)
    fun removeAt(index: Int) = controller?.removeMediaItem(index)
    fun moveItem(from: Int, to: Int) = controller?.moveMediaItem(from, to)

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun isLiked(song: Song): Flow<Boolean> = library.isLiked(song.id)

    fun setLiked(song: Song, liked: Boolean) {
        viewModelScope.launch { library.setLiked(song, liked) }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}
