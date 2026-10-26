package me.luzility.muuusic.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import me.luzility.muuusic.data.model.MusicCollection
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.playback.DesktopPlayer

sealed interface Route {
    data object Home : Route
    data object Search : Route
    data object Library : Route
    data object Settings : Route
    data class Collection(val collection: MusicCollection) : Route
    data class Artist(val name: String, val url: String?) : Route
    data class Playlist(val id: Long) : Route
}

class Navigator {
    val stack = mutableStateListOf<Route>(Route.Home)
    var queueOpen by mutableStateOf(false)

    val current: Route get() = stack.last()
    val tab: Route get() = stack.first()
    val canGoBack: Boolean get() = stack.size > 1

    fun open(route: Route) {
        if (stack.last() != route) stack.add(route)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun selectTab(route: Route) {
        if (stack.first() == route && stack.size > 1) {
            stack.removeRange(1, stack.size)
            return
        }
        stack.clear()
        stack.add(route)
    }

    fun openArtist(name: String, url: String?) = open(Route.Artist(name, url))

    fun openCollection(collection: MusicCollection) = open(Route.Collection(collection))
}

val LocalServices = staticCompositionLocalOf<DesktopServices> { error("DesktopServices not provided") }
val LocalPlayer = staticCompositionLocalOf<DesktopPlayer> { error("DesktopPlayer not provided") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
