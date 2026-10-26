package me.luzility.muuusic.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.luzility.muuusic.desktop.DesktopServices
import me.luzility.muuusic.desktop.playback.DesktopPlayer
import me.luzility.muuusic.desktop.ui.pages.ArtistPage
import me.luzility.muuusic.desktop.ui.pages.CollectionPage
import me.luzility.muuusic.desktop.ui.pages.HomeModel
import me.luzility.muuusic.desktop.ui.pages.HomePage
import me.luzility.muuusic.desktop.ui.pages.LibraryPage
import me.luzility.muuusic.desktop.ui.pages.PlaylistPage
import me.luzility.muuusic.desktop.ui.pages.SearchModel
import me.luzility.muuusic.desktop.ui.pages.SearchPage

private enum class Tab(val route: Route, val label: String, val icon: ImageVector) {
    Home(Route.Home, "nav_home", Icons.Rounded.Home),
    Search(Route.Search, "nav_search", Icons.Rounded.Search),
    Library(Route.Library, "nav_library", Icons.Rounded.LibraryMusic),
}

@Composable
fun App(services: DesktopServices, player: DesktopPlayer, navigator: Navigator) {
    val state by player.state.collectAsState()
    val prefs by services.settings.prefs.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val home = remember { HomeModel(services, scope) }
    val search = remember { SearchModel(services, scope) }

    MuuusicDesktopTheme(artworkUrl = state.current?.thumbnailUrl, prefs = prefs) {
        CompositionLocalProvider(
            LocalServices provides services,
            LocalPlayer provides player,
            LocalNavigator provides navigator,
            LocalSnackbar provides snackbar,
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            SideRail(navigator)
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                val motion = MaterialTheme.motionScheme
                                AnimatedContent(
                                    targetState = navigator.current,
                                    transitionSpec = {
                                        (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 24 }) togetherWith
                                            fadeOut(motion.fastEffectsSpec())
                                    },
                                    label = "page",
                                ) { route ->
                                    when (route) {
                                        Route.Home -> HomePage(home)
                                        Route.Search -> SearchPage(search)
                                        Route.Library -> LibraryPage()
                                        Route.Settings -> Unit
                                        is Route.Collection -> CollectionPage(route.collection)
                                        is Route.Artist -> ArtistPage(route.name, route.url)
                                        is Route.Playlist -> PlaylistPage(route.id)
                                    }
                                }
                                if (navigator.canGoBack) {
                                    FilledTonalIconButton(
                                        onClick = navigator::back,
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier.padding(12.dp),
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, str("back"))
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = navigator.queueOpen && state.current != null,
                                enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                                exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                            ) {
                                QueuePanel(onClose = { navigator.queueOpen = false })
                            }
                        }
                        AnimatedVisibility(
                            visible = state.current != null,
                            enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it },
                            exit = slideOutVertically { it },
                        ) {
                            PlayerBar(state)
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = PlayerBarHeight + 16.dp))
                }
            }
        }
    }
}

@Composable
private fun SideRail(navigator: Navigator) {
    val railState = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val expanded = railState.targetValue == WideNavigationRailValue.Expanded
    WideNavigationRail(
        state = railState,
        header = {
            IconButton(
                onClick = { scope.launch { if (expanded) railState.collapse() else railState.expand() } },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 24.dp),
            ) {
                Icon(if (expanded) Icons.AutoMirrored.Rounded.MenuOpen else Icons.Rounded.Menu, null)
            }
        },
    ) {
        Tab.entries.forEach { tab ->
            WideNavigationRailItem(
                railExpanded = expanded,
                selected = navigator.tab == tab.route,
                onClick = { navigator.selectTab(tab.route) },
                icon = { Icon(tab.icon, null) },
                label = { Text(str(tab.label)) },
            )
        }
    }
}
