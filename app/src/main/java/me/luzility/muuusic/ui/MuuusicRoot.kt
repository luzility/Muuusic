package me.luzility.muuusic.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedContentScope
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.AnimationVector1D
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.compose.animation.scaleOut
import me.luzility.muuusic.ui.onboarding.OnboardingScreen
import me.luzility.muuusic.ui.update.UpdatePrompt
import me.luzility.muuusic.ui.recognition.RecognitionScreen
import me.luzility.muuusic.ui.download.DownloadsScreen
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import android.net.Uri
import me.luzility.muuusic.ui.components.TopBackHandler
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
            UpdatePrompt()
            val nav = rememberNavController()
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
            // 0 = mini bar, 1 = full-screen player.
            val sheetProgress = rememberSaveable(saver = SheetProgressSaver) { Animatable(0f) }
            var navBarHeight by remember { mutableStateOf(0.dp) }
            val hasSong = state.current != null

            LaunchedEffect(hasSong) { if (!hasSong) sheetProgress.snapTo(0f) }

            fun go(route: String, collapsePlayer: Boolean = false) {
                if (nav.currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
                if (collapsePlayer) scope.launch { sheetProgress.animateTo(0f, spring) }
                nav.navigate(route)
            }

            val navigator = remember(nav) {
                AppNavigator(
                    openArtist = { name, url -> go("artist?url=${Uri.encode(url.orEmpty())}&name=${Uri.encode(name)}", collapsePlayer = true) },
                    openCollection = { c -> go("collection?url=${Uri.encode(c.url)}&type=${c.type.name}", collapsePlayer = true) },
                    openPlaylist = { id -> go("playlist/$id", collapsePlayer = true) },
                    openDownloads = { go("downloads") },
                    openRecognition = { go("recognize") },
                )
            }

            CompositionLocalProvider(LocalNavigator provides navigator) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                // Test tags double as resource ids so UI Automator (baseline profile journeys) can find them.
                Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    Scaffold(
                        bottomBar = {
                            Column(Modifier.onSizeChanged { navBarHeight = with(density) { it.height.toDp() } }) {
                            OfflineStrip()
                            ShortNavigationBar {
                                val stack by nav.currentBackStack.collectAsStateWithLifecycle()
                                val currentTab = stack.lastOrNull { entry -> Tab.entries.any { it.route == entry.destination.route } }?.destination?.route
                                Tab.entries.forEach { tab ->
                                    val selected = currentTab == tab.route
                                    ShortNavigationBarItem(
                                        modifier = Modifier.testTag("nav_${tab.route}"),
                                        selected = selected,
                                        onClick = {
                                            if (selected) {
                                                nav.popBackStack(tab.route, inclusive = false)
                                            } else {
                                                nav.navigate(tab.route) {
                                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        },
                                        icon = { Icon(tab.icon, null) },
                                        label = { Text(stringResource(tab.label)) },
                                    )
                                }
                            }
                            }
                        },
                    ) { padding ->
                        // Pages run under the floating mini player; lists pad themselves by its height.
                        val miniSpace = if (hasSong) MiniPlayerHeight + 16.dp else 0.dp
                        CompositionLocalProvider(LocalMiniPlayerInset provides miniSpace) {
                        val transitions = rememberPageTransitions()
                        NavHost(
                            navController = nav,
                            startDestination = Tab.Home.route,
                            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                            enterTransition = transitions.enter,
                            exitTransition = transitions.exit,
                            popEnterTransition = transitions.popEnter,
                            popExitTransition = transitions.popExit,
                        ) {
                            page(Tab.Home.route) { HomeScreen(onOpenSettings = { go("settings") }) }
                            page("settings") {
                                SettingsScreen(onBack = dropUnlessResumed { nav.popBackStack() }, onOpenEqualizer = { go("equalizer") })
                            }
                            page("equalizer") { EqualizerScreen(onBack = dropUnlessResumed { nav.popBackStack() }) }
                            page("downloads") { DownloadsScreen(onBack = dropUnlessResumed { nav.popBackStack() }) }
                            page(
                                route = "playlist/{id}",
                                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                            ) {
                                PlaylistScreen(onBack = dropUnlessResumed { nav.popBackStack() })
                            }
                            page(Tab.Search.route) { entry ->
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
                            page("recognize") { entry ->
                                RecognitionScreen(
                                    onClose = dropUnlessResumed { nav.popBackStack() },
                                    onResult = { query ->
                                        if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                            nav.previousBackStackEntry?.savedStateHandle?.set(RecognizedQueryKey, query)
                                            nav.popBackStack()
                                        }
                                    },
                                )
                            }
                            page(Tab.Library.route) { LibraryScreen() }
                            page(
                                route = "collection?url={url}&type={type}",
                                arguments = listOf(
                                    navArgument("url") { type = NavType.StringType },
                                    navArgument("type") { type = NavType.StringType },
                                ),
                            ) {
                                CollectionScreen(onBack = dropUnlessResumed { nav.popBackStack() })
                            }
                            page(
                                route = "artist?url={url}&name={name}",
                                arguments = listOf(
                                    navArgument("url") { type = NavType.StringType; defaultValue = "" },
                                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                                ),
                            ) {
                                ArtistScreen(onBack = dropUnlessResumed { nav.popBackStack() })
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
            TopBackHandler(enabled = sheetProgress.targetValue > 0.5f) {
                scope.launch { sheetProgress.animateTo(0f, spring) }
            }

            val settings = LocalContext.current.container.settings
            AnimatedVisibility(
                visible = !appPrefs.onboarded,
                enter = fadeIn(),
                exit = fadeOut() + scaleOut(targetScale = 1.05f),
            ) {
                OnboardingScreen(onDone = { settings.update { it.copy(onboarded = true) } })
            }
        }
    }
}

private val SheetProgressSaver = Saver<Animatable<Float, AnimationVector1D>, Float>(
    save = { if (it.targetValue > 0.5f) 1f else 0f },
    restore = { Animatable(it) },
)

@Composable
private fun OfflineStrip() {
    val online by LocalContext.current.container.connectivity.online.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = !online,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.defaultEffectsSpec()),
    ) {
        Surface(color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CloudOff, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.offline_banner), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private class PageTransitions(
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    Tab.entries.any { it.route == initialState.destination.route } && Tab.entries.any { it.route == targetState.destination.route }

@Composable
private fun rememberPageTransitions(): PageTransitions {
    val motion = MaterialTheme.motionScheme
    val slide = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = PageStiffness, visibilityThreshold = IntOffset.VisibilityThreshold)
    val depth = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = PageStiffness)
    val fadeIn = motion.defaultEffectsSpec<Float>()
    val fadeOut = motion.fastEffectsSpec<Float>()
    val tabScale = motion.fastSpatialSpec<Float>()
    return remember(motion) {
        val recede = scaleOut(depth, targetScale = 0.92f) + fadeOut(depth, targetAlpha = 0.5f)
        val emerge = scaleIn(depth, initialScale = 0.92f) + fadeIn(depth, initialAlpha = 0.5f)
        PageTransitions(
            enter = {
                if (isTabSwitch()) fadeIn(fadeIn) + scaleIn(tabScale, initialScale = 0.97f) else slideInHorizontally(slide) { it }
            },
            exit = {
                if (isTabSwitch()) fadeOut(fadeOut) else slideOutHorizontally(slide) { -it / 5 } + recede
            },
            popEnter = {
                if (isTabSwitch()) fadeIn(fadeIn) + scaleIn(tabScale, initialScale = 0.97f) else slideInHorizontally(slide) { -it / 5 } + emerge
            },
            popExit = {
                if (isTabSwitch()) fadeOut(fadeOut) else slideOutHorizontally(slide) { it }
            },
        )
    }
}

private const val PageStiffness = 550f

private fun NavGraphBuilder.page(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    val scope = this
    val corner by transition.animateDp(label = "pageCorner") { if (it == EnterExitState.Visible) 0.dp else 28.dp }
    val shape = RoundedCornerShape(corner)
    Box(
        Modifier
            .fillMaxSize()
            .shadow(if (corner > 0.dp) 12.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
    ) { scope.content(entry) }
}
