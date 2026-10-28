package me.luzility.muuusic.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.Variant

@DBusInterfaceName("org.kde.StatusNotifierItem")
interface StatusNotifierItem : DBusInterface {
    fun ContextMenu(x: Int, y: Int)
    fun Activate(x: Int, y: Int)
    fun SecondaryActivate(x: Int, y: Int)
    fun Scroll(delta: Int, orientation: String)

    class NewToolTip(path: String) : DBusSignal(path)
}

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
interface StatusNotifierWatcher : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)
}

class IconPixmap(
    @field:Position(0) val width: Int,
    @field:Position(1) val height: Int,
    @field:Position(2) val data: ByteArray,
) : Struct()

class ToolTip(
    @field:Position(0) val iconName: String,
    @field:Position(1) val image: List<IconPixmap>,
    @field:Position(2) val title: String,
    @field:Position(3) val text: String,
) : Struct()

class StatusNotifierTray(
    private val scope: CoroutineScope,
    private val player: DesktopPlayer,
    private val onActivate: () -> Unit,
    private val onMenu: (x: Int, y: Int) -> Unit,
) : AppTray, StatusNotifierItem, Properties {
    private var connection: DBusConnection? = null
    private val _available = MutableStateFlow(false)
    override val available: StateFlow<Boolean> = _available.asStateFlow()
    private val icons: List<IconPixmap> by lazy { loadIcons() }

    init {
        scope.launch(Dispatchers.IO) {
            val bus = runCatching { DBusConnectionBuilder.forSessionBus().withShared(false).build() }.getOrNull() ?: return@launch
            val name = "org.kde.StatusNotifierItem-${ProcessHandle.current().pid()}-1"
            val registered = runCatching {
                bus.requestBusName(name)
                bus.exportObject(PATH, this@StatusNotifierTray)
                bus.getRemoteObject(WATCHER, WATCHER_PATH, StatusNotifierWatcher::class.java).RegisterStatusNotifierItem(name)
            }.isSuccess
            if (!registered) {
                bus.close()
                return@launch
            }
            connection = bus
            _available.value = true
            player.state.distinctUntilChangedBy { it.current?.id }.collect {
                runCatching { bus.sendMessage(StatusNotifierItem.NewToolTip(PATH)) }
            }
        }
    }

    override fun close() {
        _available.value = false
        runCatching { connection?.close() }
    }

    override fun getObjectPath() = PATH

    override fun ContextMenu(x: Int, y: Int) = onMain { onMenu(x, y) }

    override fun Activate(x: Int, y: Int) = onMain { onActivate() }

    override fun SecondaryActivate(x: Int, y: Int) = onMain { player.togglePlay() }

    override fun Scroll(delta: Int, orientation: String) = onMain {
        if (orientation.equals("vertical", ignoreCase = true)) {
            player.setVolume((player.volume.value + delta / 1200f).coerceIn(0f, 1f))
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A : Any?> Get(interfaceName: String, propertyName: String): A = properties()[propertyName] as A

    override fun <A : Any?> Set(interfaceName: String, propertyName: String, value: A) = Unit

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = properties()

    private fun properties(): Map<String, Variant<*>> {
        val song = player.state.value.current
        return mapOf(
            "Category" to Variant("ApplicationStatus"),
            "Id" to Variant("muuusic"),
            "Title" to Variant("Muuusic"),
            "Status" to Variant("Active"),
            "WindowId" to Variant(0),
            "IconName" to Variant(""),
            "IconPixmap" to Variant(icons, "a(iiay)"),
            "ItemIsMenu" to Variant(false),
            "Menu" to Variant(DBusPath("/NO_DBUSMENU")),
            "ToolTip" to Variant(
                ToolTip("", emptyList(), song?.title ?: "Muuusic", song?.artist.orEmpty()),
                "(sa(iiay)ss)",
            ),
        )
    }

    private fun loadIcons(): List<IconPixmap> = listOf(22, 32, 48, 64).mapNotNull { size ->
        val image = trayIcon(size) ?: return@mapNotNull null
        val data = ByteArray(size * size * 4)
        image.getRGB(0, 0, size, size, null, 0, size).forEachIndexed { i, argb ->
            data[i * 4] = (argb ushr 24).toByte()
            data[i * 4 + 1] = (argb ushr 16).toByte()
            data[i * 4 + 2] = (argb ushr 8).toByte()
            data[i * 4 + 3] = argb.toByte()
        }
        IconPixmap(size, size, data)
    }

    private fun onMain(block: () -> Unit) {
        scope.launch(Dispatchers.Main) { block() }
    }

    private companion object {
        const val PATH = "/StatusNotifierItem"
        const val WATCHER = "org.kde.StatusNotifierWatcher"
        const val WATCHER_PATH = "/StatusNotifierWatcher"
    }
}
