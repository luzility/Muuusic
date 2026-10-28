package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import me.luzility.muuusic.desktop.playback.PlayerState
import me.luzility.muuusic.desktop.playback.RepeatMode
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)

    class Seeked(path: String, val position: Long) : DBusSignal(path, position)
}

class Mpris(
    private val scope: CoroutineScope,
    private val player: DesktopPlayer,
    private val onRaise: () -> Unit,
    private val onQuit: () -> Unit,
) : MprisRoot, MprisPlayer, Properties {
    private var connection: DBusConnection? = null

    init {
        scope.launch(Dispatchers.IO) {
            val bus = runCatching { DBusConnectionBuilder.forSessionBus().withShared(false).build() }.getOrNull() ?: return@launch
            val exported = runCatching {
                bus.requestBusName(BUS_NAME)
                bus.exportObject(PATH, this@Mpris)
            }.isSuccess
            if (!exported) {
                bus.close()
                return@launch
            }
            connection = bus
            withContext(Dispatchers.Main) { watch() }
        }
    }

    fun close() {
        runCatching { connection?.close() }
    }

    override fun getObjectPath() = PATH

    override fun Raise() = onMain { onRaise() }

    override fun Quit() = onMain { onQuit() }

    override fun Next() = onMain { player.next() }

    override fun Previous() = onMain { player.previous() }

    override fun Pause() = onMain { player.pause() }

    override fun PlayPause() = onMain { player.togglePlay() }

    override fun Stop() = onMain { player.pause() }

    override fun Play() = onMain { if (!player.state.value.isPlaying) player.togglePlay() }

    override fun Seek(offset: Long) = onMain { player.seekTo((player.position() + offset / 1000).coerceAtLeast(0)) }

    override fun SetPosition(trackId: DBusPath, position: Long) = onMain {
        if (trackId.path == trackPath(player.state.value)) player.seekTo(position / 1000)
    }

    override fun OpenUri(uri: String) = Unit

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A =
        properties(interfaceName)[propertyName] as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) = onMain {
        when (propertyName) {
            "Volume" -> (value as? Double)?.let { player.setVolume(it.toFloat().coerceIn(0f, 1f)) }
            "Shuffle" -> if (value as? Boolean != player.state.value.shuffle) player.toggleShuffle()
            "LoopStatus" -> {
                val wanted = when (value as? String) {
                    "Track" -> RepeatMode.One
                    "Playlist" -> RepeatMode.All
                    else -> RepeatMode.Off
                }
                repeat(RepeatMode.entries.size) { if (player.state.value.repeatMode != wanted) player.cycleRepeat() }
            }
        }
    }

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties(interfaceName)

    private fun watch() {
        scope.launch {
            player.state.collect { state ->
                changed(PLAYER_INTERFACE, playerProperties(state, player.volume.value))
            }
        }
        scope.launch {
            player.volume.collect { changed(PLAYER_INTERFACE, mapOf("Volume" to Variant(it.toDouble()))) }
        }
        scope.launch {
            player.seeks.collect { emit(MprisPlayer.Seeked(PATH, it * 1000)) }
        }
    }

    private fun properties(interfaceName: String): Map<String, Variant<*>> = when (interfaceName) {
        ROOT_INTERFACE -> mapOf(
            "CanQuit" to Variant(true),
            "CanRaise" to Variant(true),
            "HasTrackList" to Variant(false),
            "Identity" to Variant("Muuusic"),
            "DesktopEntry" to Variant("muuusic"),
            "SupportedUriSchemes" to Variant(emptyList<String>(), "as"),
            "SupportedMimeTypes" to Variant(emptyList<String>(), "as"),
        )
        PLAYER_INTERFACE -> playerProperties(player.state.value, player.volume.value) + mapOf(
            "Position" to Variant(player.position() * 1000),
        )
        else -> emptyMap()
    }

    private fun playerProperties(state: PlayerState, volume: Float): Map<String, Variant<*>> {
        val song = state.current
        val position = state.playOrder.indexOf(state.currentIndex)
        return mapOf(
            "PlaybackStatus" to Variant(
                when {
                    song == null -> "Stopped"
                    state.isPlaying || state.isBuffering -> "Playing"
                    else -> "Paused"
                },
            ),
            "LoopStatus" to Variant(
                when (state.repeatMode) {
                    RepeatMode.Off -> "None"
                    RepeatMode.One -> "Track"
                    RepeatMode.All -> "Playlist"
                },
            ),
            "Rate" to Variant(1.0),
            "Shuffle" to Variant(state.shuffle),
            "Metadata" to Variant(metadata(state), "a{sv}"),
            "Volume" to Variant(volume.toDouble()),
            "MinimumRate" to Variant(1.0),
            "MaximumRate" to Variant(1.0),
            "CanGoNext" to Variant(song != null && (position + 1 < state.playOrder.size || state.repeatMode == RepeatMode.All)),
            "CanGoPrevious" to Variant(song != null),
            "CanPlay" to Variant(song != null),
            "CanPause" to Variant(song != null),
            "CanSeek" to Variant(song != null && state.durationMs > 0),
            "CanControl" to Variant(true),
        )
    }

    private fun metadata(state: PlayerState): Map<String, Variant<*>> {
        val song = state.current ?: return mapOf("mpris:trackid" to Variant(DBusPath(NO_TRACK)))
        return buildMap {
            put("mpris:trackid", Variant(DBusPath(trackPath(state))))
            if (state.durationMs > 0) put("mpris:length", Variant(state.durationMs * 1000))
            put("xesam:title", Variant(song.title))
            put("xesam:artist", Variant(listOf(song.artist), "as"))
            put("xesam:url", Variant("https://music.youtube.com/watch?v=${song.id}"))
            song.thumbnailUrl?.let { put("mpris:artUrl", Variant(it)) }
        }
    }

    private fun trackPath(state: PlayerState): String {
        val song = state.current ?: return NO_TRACK
        return "$PATH/track/" + song.id.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
    }

    private fun changed(interfaceName: String, values: Map<String, Variant<*>>) {
        emit(Properties.PropertiesChanged(PATH, interfaceName, values, emptyList()))
    }

    private fun emit(signal: DBusSignal) {
        val bus = connection ?: return
        scope.launch(Dispatchers.IO) { runCatching { bus.sendMessage(signal) } }
    }

    private fun onMain(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    private companion object {
        const val BUS_NAME = "org.mpris.MediaPlayer2.muuusic"
        const val PATH = "/org/mpris/MediaPlayer2"
        const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        const val NO_TRACK = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
    }
}
