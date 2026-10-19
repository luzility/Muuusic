package me.luzility.muuusic.ui

import me.luzility.muuusic.ui.recognition.RecognitionScreen
import me.luzility.muuusic.ui.download.DownloadsScreen
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import me.luzility.muuusic.ui.playlist.PlaylistScreen
import me.luzility.muuusic.ui.settings.EqualizerScreen
import me.luzility.muuusic.ui.settings.SettingsScreen
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.container
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import me.luzility.muuusic.R
import me.luzility.muuusic.playback.PlayerViewModel
import me.luzility.muuusic.ui.artist.ArtistScreen
import me.luzility.muuusic.ui.collection.CollectionScreen
import me.luzility.muuusic.ui.components.AppNavigator
import me.luzility.muuusic.ui.components.LocalNavigator
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.home.HomeScreen
import me.luzility.muuusic.ui.library.LibraryScreen
import me.luzility.muuusic.ui.player.MiniPlayerHeight
import me.luzility.muuusic.ui.player.PlayerSheet
import me.luzility.muuusic.ui.search.SearchScreen
import me.luzility.muuusic.ui.theme.MuuusicTheme

private const val RecognizedQueryKey = "recognizedQuery"

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    Home("home", R.string.nav_home, Icons.Rounded.Home),
    Search("search", R.string.nav_search, Icons.Rounded.Search),
    Library("library", R.string.nav_library, Icons.Rounded.LibraryMusic),
}

@Composable
fun MuuusicRoot(player: PlayerViewModel) {
    val state by player.state.collectAsStateWithLifecycle()
    val appPrefs by LocalContext.current.container.settings.prefs.collectAsStateWithLifecycle()

    MuuusicTheme(artworkUrl = state.current?.thumbnailUrl, prefs = appPrefs) {
        CompositionLocalProvider(LocalPlayer provides player) {
            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            // 0 = mini bar, 1 = full-screen player.
            val sheetProgress = remember { Animatable(0f) }
            var navBarHeight by remember { mutableStateOf(0.dp) }
            val hasSong = state.current != null

            LaunchedEffect(hasSong) { if (!hasSong) sheetProgress.snapTo(0f) }

            val navigator = remember(nav) {
                AppNavigator(
                    openArtist = { name, url ->
                        // Opening a page from the full-screen player first tucks the player away.
                        scope.launch { sheetProgress.animateTo(0f, spring) }
                        nav.navigate("artist?url=${Uri.encode(url.orEmpty())}&name=${Uri.encode(name)}")
                    },
                    openCollection = { c ->
                        scope.launch { sheetProgress.animateTo(0f, spring) }
                        nav.navigate("collection?url=${Uri.encode(c.url)}&type=${c.type.name}")
                    },
                    openPlaylist = { id ->
                        scope.launch { sheetProgress.animateTo(0f, spring) }
                        nav.navigate("playlist/$id")
                    },
                    openDownloads = { nav.navigate("downloads") },
                    openRecognition = { nav.navigate("recognize") },
                )
            }

            CompositionLocalProvider(LocalNavigator provides navigator) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                // Test tags double as resource ids so UI Automator (baseline profile journeys) can find them.
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    Scaffold(
                        bottomBar = {
                            ShortNavigationBar(
                                modifier = Modifier.onSizeChanged { navBarHeight = with(density) { it.height.toDp() } },
                            ) {
                                Tab.entries.forEach { tab ->
                                    val selected = backStack?.destination?.route == tab.route
                                    ShortNavigationBarItem(
                                        modifier = Modifier.testTag("nav_${tab.route}"),
                                        selected = selected,
                                        onClick = {
                                            nav.navigate(tab.route) {
                                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = { Icon(tab.icon, null) },
                                        label = { Text(stringResource(tab.label)) },
                                    )
                                }
                            }
                        },
                    ) { padding ->
                        // Pages run under the floating mini player; lists pad themselves by its height.
                        val miniSpace = if (hasSong) MiniPlayerHeight + 16.dp else 0.dp
                        CompositionLocalProvider(LocalMiniPlayerInset provides miniSpace) {
                        NavHost(
                            navController = nav,
                            startDestination = Tab.Home.route,
                            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                        ) {
                            composable(Tab.Home.route) { HomeScreen(onOpenSettings = { nav.navigate("settings") }) }
                            composable("settings") {
                                SettingsScreen(onBack = { nav.popBackStack() }, onOpenEqualizer = { nav.navigate("equalizer") })
                            }
                            composable("equalizer") { EqualizerScreen(onBack = { nav.popBackStack() }) }
                            composable("downloads") { DownloadsScreen(onBack = { nav.popBackStack() }) }
                            composable(
                                route = "playlist/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                            ) {
                                PlaylistScreen(onBack = { nav.popBackStack() })
                            }
                            composable(Tab.Search.route) { entry ->
                                val recognized by entry.savedStateHandle
                                    .getStateFlow<String?>(RecognizedQueryKey, null)
                                    .collectAsStateWithLifecycle()
                                SearchScreen(
                                    onOpenCollection = navigator.openCollection,
                                    onRecognize = navigator.openRecognition,
                                    recognizedQuery = recognized,
                                    onRecognizedConsumed = { entry.savedStateHandle[RecognizedQueryKey] = null },
                                )
                            }
                            composable("recognize") {
                                RecognitionScreen(
                                    onClose = { nav.popBackStack() },
                                    onResult = { query ->
                                        nav.previousBackStackEntry?.savedStateHandle?.set(RecognizedQueryKey, query)
                                        nav.popBackStack()
                                    },
                                )
                            }
                            composable(Tab.Library.route) { LibraryScreen() }
                            composable(
                                route = "collection?url={url}&type={type}",
                                arguments = listOf(
                                    navArgument("url") { type = NavType.StringType },
                                    navArgument("type") { type = NavType.StringType },
                                ),
                            ) {
                                CollectionScreen(onBack = { nav.popBackStack() })
                            }
                            composable(
                                route = "artist?url={url}&name={name}",
                                arguments = listOf(
                                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                                ),
                            ) {
                                ArtistScreen(onBack = { nav.popBackStack() })
                            }
                        }
                        }
                    }

                    // Content fades out under the floating bar: transparent at the top, the nav bar's
                    // colour at the bottom, so the bar never sits on a hard cut of list items.
                    AnimatedVisibility(
                        visible = hasSong,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = navBarHeight),
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        val fadeColor = MaterialTheme.colorScheme.surfaceContainer
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(MiniPlayerHeight + 56.dp)
                                .background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to fadeColor, 1f to fadeColor)),
                        )
                    }

                    AnimatedVisibility(
                        visible = hasSong,
                        enter = fadeIn() + slideInVertically { it / 8 },
                        exit = fadeOut(),
                    ) {
                        PlayerSheet(
                            state = state,
                            progress = sheetProgress,
                            bottomInset = navBarHeight,
                            onDismiss = player::dismiss,
                        )
                    }
                }
            }
            }
            BackHandler(enabled = sheetProgress.targetValue > 0.5f) {
                scope.launch { sheetProgress.animateTo(0f, spring) }
            }
        }
    }
}
