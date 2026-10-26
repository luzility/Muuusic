package me.luzility.muuusic.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.data.sponsorblock.Segment
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.Mpv
import me.luzility.muuusic.desktop.MpvEvent
import me.luzility.muuusic.data.youtube.NewPipeDownloader
import me.luzility.muuusic.playback.SleepTimer
import me.luzility.muuusic.playback.audio.Karaoke
import java.io.File
import java.util.Locale
import kotlin.math.pow

enum class RepeatMode { Off, All, One }

enum class QueueResult { Added, Moved, AlreadyPlaying }

data class PlayerState(
    val current: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = 0,
    val playOrder: List<Int> = emptyList(),
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
)

class DesktopPlayer(
    private val services: DesktopServices,
    private val localFile: (String) -> File? = { null },
) {
    private val settings = services.settings
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private inner class Deck {
        val mpv = Mpv { event -> scope.launch { onEvent(this@Deck, event) } }
        var song: Song? = null
        var hasFile = false
        var paused = false
        var buffering = false
        var durationMs = 0L
        var fade = 1.0

        fun position(): Long = if (song == null || !hasFile) 0 else ((mpv.getDouble("time-pos") ?: 0.0) * 1000).toLong().coerceAtLeast(0)

        fun applyVolume() {
            mpv.set("volume", decimal(_volume.value * sleepFactor * fade * 100.0))
        }

        fun stop() {
            mpv.command("stop")
            song = null
            hasFile = false
            buffering = false
            durationMs = 0
        }
    }

    private var active = Deck()
    private var standby = Deck()
    private var fadingOut = false

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _sleepEndsAt = MutableStateFlow(0L)
    val sleepEndsAt: StateFlow<Long> = _sleepEndsAt.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var queue = mutableListOf<Song>()
    private var order = mutableListOf<Int>()
    private var current = -1
    private var shuffle = false
    private var repeat = RepeatMode.Off
    private var loading = false
    private var fadingTo = -1
    private var fadeJob: Job? = null
    private var radioJob: Job? = null
    private var sleepJob: Job? = null
    private var sleepFactor = 1.0
    private var pauseAtEnd = false
    private var failuresInRow = 0
    private var segments: List<Segment> = emptyList()
    private var segmentJob: Job? = null
    private var normalizeJob: Job? = null

    init {
        listOf(active, standby).forEach { it.mpv.set("user-agent", NewPipeDownloader.USER_AGENT) }
        scope.launch {
            settings.prefs.map { Triple(it.playbackSpeed, it.linkPitch, it.pitchSemitones) }.distinctUntilChanged().collect { applyFilters() }
        }
        scope.launch {
            settings.prefs.map { DesktopEqualizer.filters(it) }.distinctUntilChanged().collect { applyFilters() }
        }
        scope.launch {
            settings.prefs.collect { prefs ->
                Karaoke.setStrength(prefs.karaokeStrength)
                if (!prefs.karaoke) Karaoke.set(false)
            }
        }
        scope.launch {
            combine(Karaoke.enabled, Karaoke.strength) { on, strength -> on to strength }.distinctUntilChanged().collect { applyFilters() }
        }
        scope.launch {
            settings.prefs.map { it.normalizeVolume }.distinctUntilChanged().collect { applyGain(active) }
        }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                skipSegment()
                maybeStartFade()
            }
        }
    }

    fun position(): Long = active.position()

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        queue = songs.toMutableList()
        this.shuffle = shuffle
        val start = if (shuffle) songs.indices.random() else startIndex.coerceIn(songs.indices)
        order = buildOrder(start)
        load(start)
    }

    fun startRadio(song: Song) {
        play(listOf(song))
        fetchRadio(song.id)
    }

    fun enqueue(song: Song, next: Boolean): QueueResult {
        if (queue.isEmpty()) {
            play(listOf(song))
            return QueueResult.Added
        }
        if (queue.getOrNull(current)?.id == song.id) return QueueResult.AlreadyPlaying
        cancelFade()
        val existing = queue.indices.firstOrNull { it != current && queue[it].id == song.id }
        val index = existing ?: queue.size.also { queue.add(song) }
        order.remove(index)
        val at = if (next) order.indexOf(current) + 1 else order.size
        order.add(at.coerceIn(0, order.size), index)
        if (!shuffle) reindexToOrder()
        publish()
        return if (existing != null) QueueResult.Moved else QueueResult.Added
    }

    fun togglePlay() {
        if (current < 0 || loading) return
        if (!active.hasFile) {
            load(current)
            return
        }
        val pause = !active.paused
        active.mpv.set("pause", if (pause) "yes" else "no")
        if (fadingOut) standby.mpv.set("pause", if (pause) "yes" else "no")
    }

    fun pause() {
        active.mpv.set("pause", "yes")
        if (fadingOut) standby.mpv.set("pause", "yes")
    }

    fun next() {
        val target = nextIndex() ?: return
        load(target)
    }

    fun previous() {
        val position = order.indexOf(current)
        if (position() > RESTART_THRESHOLD_MS || position <= 0) {
            seekTo(0)
            return
        }
        load(order[position - 1])
    }

    fun seekTo(positionMs: Long) {
        cancelFade()
        if (active.hasFile) active.mpv.command("seek", (positionMs / 1000.0).toString(), "absolute")
        publish()
    }

    fun skipTo(index: Int) {
        if (index in queue.indices) load(index)
    }

    fun removeAt(index: Int) {
        if (index !in queue.indices) return
        cancelFade()
        val wasCurrent = index == current
        val nextInOrder = order.getOrNull(order.indexOf(index) + 1)
        queue.removeAt(index)
        order.remove(index)
        order = order.map { if (it > index) it - 1 else it }.toMutableList()
        if (queue.isEmpty()) {
            dismiss()
            return
        }
        if (wasCurrent) {
            load(nextInOrder?.let { if (it > index) it - 1 else it } ?: order.first())
        } else {
            if (current > index) current--
            publish()
        }
    }

    fun moveInPlayOrder(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        cancelFade()
        order.add(to, order.removeAt(from))
        if (!shuffle) reindexToOrder()
        publish()
    }

    fun toggleShuffle() {
        cancelFade()
        shuffle = !shuffle
        order = if (shuffle) buildOrder(current) else queue.indices.toMutableList()
        publish()
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
        if (repeat == RepeatMode.One) cancelFade()
        publish()
    }

    fun setVolume(value: Float) {
        _volume.value = value.coerceIn(0f, 1f)
        active.applyVolume()
        standby.applyVolume()
    }

    fun setSleepTimer(minutes: Int) {
        clearSleepTimer()
        when {
            minutes == SleepTimer.END_OF_SONG -> {
                pauseAtEnd = true
                _sleepEndsAt.value = SleepTimer.ENDS_WITH_SONG
            }
            minutes > 0 -> {
                val endsAt = System.currentTimeMillis() + minutes * 60_000L
                _sleepEndsAt.value = endsAt
                sleepJob = scope.launch {
                    delay((endsAt - System.currentTimeMillis() - SleepTimer.FADE_MS).coerceAtLeast(0))
                    val steps = 40
                    repeat(steps) { step ->
                        sleepFactor = 1.0 - (step + 1.0) / steps
                        active.applyVolume()
                        standby.applyVolume()
                        delay(SleepTimer.FADE_MS / steps)
                    }
                    pause()
                    clearSleepTimer()
                }
            }
        }
    }

    fun dismiss() {
        cancelFade()
        radioJob?.cancel()
        active.stop()
        queue = mutableListOf()
        order = mutableListOf()
        current = -1
        loading = false
        publish()
    }

    fun release() {
        scope.cancel()
        active.mpv.destroy()
        standby.mpv.destroy()
    }

    private fun load(index: Int) {
        val song = queue.getOrNull(index) ?: return
        cancelFade()
        current = index
        loading = true
        active.song = song
        active.hasFile = false
        active.durationMs = song.durationSec * 1000
        active.fade = 1.0
        active.applyVolume()
        startOn(active, song)
        loadSegments(song.id)
        publish()
        scope.launch(Dispatchers.IO) { services.library.recordPlay(song) }
        extendQueueIfNeeded()
    }

    private fun startOn(deck: Deck, song: Song, paused: Boolean = false) {
        applyGain(deck)
        deck.mpv.set("pause", if (paused) "yes" else "no")
        deck.mpv.command("loadfile", localFile(song.id)?.absolutePath ?: services.streamCache.urlFor(song.id), "replace")
    }

    private fun onEvent(deck: Deck, event: MpvEvent) {
        when (event) {
            MpvEvent.FileLoaded -> deck.hasFile = true
            MpvEvent.PlaybackRestart -> {
                deck.buffering = false
                if (deck === active) {
                    loading = false
                    failuresInRow = 0
                }
            }
            is MpvEvent.EndFile -> {
                if (deck.song == null) return
                when (event.reason) {
                    Mpv.END_EOF -> {
                        deck.hasFile = false
                        if (deck === active) onFinished()
                    }
                    Mpv.END_ERROR -> {
                        deck.hasFile = false
                        if (deck === active) onError() else cancelFade()
                    }
                }
            }
            is MpvEvent.Property -> when (event.name) {
                "pause" -> deck.paused = event.value == true
                "paused-for-cache" -> deck.buffering = event.value == true
                "duration" -> (event.value as? Double)?.let { if (it > 0) deck.durationMs = (it * 1000).toLong() }
            }
        }
        if (deck === active) publish()
    }

    private fun maybeStartFade() {
        val fadeMs = settings.prefs.value.fadeSeconds * 1000L
        if (fadeMs <= 0 || fadingOut || loading || repeat == RepeatMode.One || pauseAtEnd) return
        if (!active.hasFile || active.paused || active.durationMs <= fadeMs * 2) return
        val remaining = active.durationMs - active.position()
        if (fadingTo < 0) {
            if (remaining > fadeMs + PRELOAD_MS) return
            val target = nextIndex() ?: return
            val song = queue.getOrNull(target) ?: return
            fadingTo = target
            standby.song = song
            standby.hasFile = false
            standby.durationMs = song.durationSec * 1000
            standby.fade = 0.0
            standby.applyVolume()
            startOn(standby, song, paused = true)
            return
        }
        if (remaining > fadeMs || fadeJob != null) return
        val incoming = standby
        val target = fadingTo
        fadeJob = scope.launch {
            val ready = withTimeoutOrNull(FADE_START_TIMEOUT_MS) {
                while (!incoming.hasFile || incoming.buffering) delay(TICK_MS)
                true
            }
            if (ready == null) {
                cancelFade()
                return@launch
            }
            incoming.mpv.set("pause", "no")
            incoming.paused = false
            val outgoing = active
            active = incoming
            standby = outgoing
            fadingTo = -1
            fadingOut = true
            current = target
            queue.getOrNull(target)?.let { song ->
                loadSegments(song.id)
                scope.launch(Dispatchers.IO) { services.library.recordPlay(song) }
            }
            extendQueueIfNeeded()
            publish()
            var elapsed = 0L
            while (elapsed < fadeMs) {
                delay(FADE_STEP_MS)
                if (active.paused) continue
                elapsed += FADE_STEP_MS
                val t = (elapsed.toDouble() / fadeMs).coerceIn(0.0, 1.0)
                outgoing.fade = 1.0 - t
                active.fade = t
                outgoing.applyVolume()
                active.applyVolume()
            }
            finishFade()
        }
    }

    private fun finishFade() {
        fadeJob = null
        fadingOut = false
        standby.stop()
        standby.fade = 1.0
        active.fade = 1.0
        active.applyVolume()
        publish()
    }

    private fun cancelFade() {
        if (fadingTo < 0 && !fadingOut) return
        fadeJob?.cancel()
        fadeJob = null
        fadingTo = -1
        fadingOut = false
        standby.stop()
        standby.fade = 1.0
        active.fade = 1.0
        active.applyVolume()
    }

    private fun onFinished() {
        if (pauseAtEnd) {
            clearSleepTimer()
            publish()
            return
        }
        if (repeat == RepeatMode.One) {
            load(current)
            return
        }
        nextIndex()?.let(::load) ?: publish()
    }

    private fun onError() {
        loading = false
        active.song?.let { services.youtube.invalidateStreamUrl(it.id) }
        publish()
        if (++failuresInRow > MAX_SKIPS_IN_ROW) return
        order.getOrNull(order.indexOf(current) + 1)?.let(::load)
    }

    private fun nextIndex(): Int? {
        val position = order.indexOf(current)
        return when {
            position + 1 < order.size -> order[position + 1]
            repeat == RepeatMode.All -> order.firstOrNull()
            else -> null
        }
    }

    private fun applyFilters() {
        val prefs = settings.prefs.value
        val filters = mutableListOf<String>()
        if (!prefs.linkPitch && prefs.pitchSemitones != 0f) {
            filters += "rubberband=pitch-scale=${decimal(2.0.pow(prefs.pitchSemitones / 12.0))}"
        }
        val graph = DesktopEqualizer.filters(prefs).toMutableList()
        if (Karaoke.enabled.value) {
            val cut = Karaoke.strength.value / 2.0
            val keep = 1.0 - cut
            graph += "pan=stereo|c0=${decimal(keep)}*c0-${decimal(cut)}*c1|c1=${decimal(keep)}*c1-${decimal(cut)}*c0"
        }
        if (graph.isNotEmpty()) filters += "lavfi=[${graph.joinToString(",")}]"
        listOf(active, standby).forEach { deck ->
            deck.mpv.set("speed", prefs.playbackSpeed.toString())
            deck.mpv.set("audio-pitch-correction", if (prefs.linkPitch) "no" else "yes")
            deck.mpv.set("af", filters.joinToString(","))
        }
    }

    private fun applyGain(deck: Deck) {
        val videoId = deck.song?.id
        if (videoId == null || !settings.prefs.value.normalizeVolume) {
            deck.mpv.set("volume-gain", "0")
            return
        }
        val known = services.loudness.get(videoId)
        deck.mpv.set("volume-gain", decimal(gainDb(known ?: 0f)))
        if (known != null) return
        if (deck === active) normalizeJob?.cancel()
        val job = scope.launch {
            val db = runCatching { services.innerTube.loudnessGainDb(videoId) }.getOrNull()?.toFloat() ?: return@launch
            services.loudness.put(videoId, db)
            if (deck.song?.id == videoId) deck.mpv.set("volume-gain", decimal(gainDb(db)))
        }
        if (deck === active) normalizeJob = job
    }

    private fun gainDb(db: Float): Double = db.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB).toDouble()

    private fun loadSegments(videoId: String) {
        segmentJob?.cancel()
        segments = emptyList()
        if (!settings.prefs.value.sponsorBlock) return
        segmentJob = scope.launch {
            val found = runCatching { services.sponsorBlock.segments(videoId) }.getOrDefault(emptyList())
            if (active.song?.id == videoId) segments = found
        }
    }

    private fun skipSegment() {
        if (segments.isEmpty() || !active.hasFile || active.paused || loading || fadingTo >= 0 || fadingOut) return
        if (!settings.prefs.value.sponsorBlock) return
        val position = position()
        val segment = segments.firstOrNull { position >= it.startMs && position < it.endMs - 500 } ?: return
        if (active.durationMs > 0 && segment.endMs >= active.durationMs - 1_000) next() else seekTo(segment.endMs)
    }

    private fun extendQueueIfNeeded() {
        if (!settings.prefs.value.autoplay || repeat != RepeatMode.Off) return
        val remaining = order.size - order.indexOf(current) - 1
        if (remaining > 1 || radioJob?.isActive == true) return
        fetchRadio(queue.getOrNull(current)?.id ?: return)
    }

    private fun fetchRadio(seedId: String) {
        radioJob?.cancel()
        radioJob = scope.launch {
            val songs = runCatching { services.youtube.radio(seedId) }.getOrNull() ?: return@launch
            val queued = queue.map { it.id }.toSet()
            val fresh = songs.filter { it.id !in queued }.take(RADIO_SIZE)
            if (fresh.isEmpty()) return@launch
            val start = queue.size
            queue.addAll(fresh)
            order.addAll(start until queue.size)
            publish()
        }
    }

    private fun buildOrder(start: Int): MutableList<Int> {
        if (!shuffle) return queue.indices.toMutableList()
        val rest = queue.indices.filter { it != start }.shuffled()
        return (listOfNotNull(start.takeIf { it in queue.indices }) + rest).toMutableList()
    }

    private fun reindexToOrder() {
        val playing = queue.getOrNull(current)
        queue = order.map { queue[it] }.toMutableList()
        order = queue.indices.toMutableList()
        current = playing?.let { song -> queue.indexOfFirst { it === song } } ?: -1
    }

    private fun clearSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        pauseAtEnd = false
        sleepFactor = 1.0
        active.applyVolume()
        standby.applyVolume()
        _sleepEndsAt.value = 0
    }

    private fun publish() {
        _state.value = PlayerState(
            current = queue.getOrNull(current),
            isPlaying = active.hasFile && !active.paused && !loading && !active.buffering,
            isBuffering = loading || active.buffering,
            durationMs = active.durationMs,
            queue = queue.toList(),
            currentIndex = current.coerceAtLeast(0),
            playOrder = order.toList(),
            shuffle = shuffle,
            repeatMode = repeat,
        )
    }

    private fun decimal(value: Double) = String.format(Locale.ROOT, "%.5f", value)

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
        const val MAX_SKIPS_IN_ROW = 3
        const val RADIO_SIZE = 25
        const val TICK_MS = 250L
        const val FADE_STEP_MS = 50L
        const val FADE_START_TIMEOUT_MS = 8_000L
        const val PRELOAD_MS = 15_000L
        const val MIN_GAIN_DB = -15f
        const val MAX_GAIN_DB = 6f
    }
}
