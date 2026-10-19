package me.luzility.muuusic.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.settings.AudioQuality
import me.luzility.muuusic.data.settings.ThemeMode
import me.luzility.muuusic.ui.components.MorphShape
import kotlin.math.floor

private class Page(val icon: ImageVector, val title: Int, val body: Int)

private val pages = listOf(
    Page(Icons.Rounded.MusicNote, R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
    Page(Icons.Rounded.NotificationsActive, R.string.onboarding_notifications_title, R.string.onboarding_notifications_body),
    Page(Icons.Rounded.Palette, R.string.onboarding_look_title, R.string.onboarding_look_body),
    Page(Icons.Rounded.GraphicEq, R.string.onboarding_sound_title, R.string.onboarding_sound_body),
)

// One shape per page; the hero morphs between neighbours as the pager moves.
private val pageShapes = listOf(
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie7Sided,
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            // Keeps taps from reaching the app underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Fixed height so the page doesn't jump when Skip goes away on the last page.
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.onboarding_skip)) }
            }

            val position = pager.currentPage + pager.currentPageOffsetFraction
            Hero(position, Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))

            HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) { index ->
                PageContent(index)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dots(pager.currentPage)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                ) {
                    AnimatedContent(last, label = "nextLabel") { isLast ->
                        Text(stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(position: Float, modifier: Modifier = Modifier) {
    val from = floor(position).toInt().coerceIn(0, pages.lastIndex)
    val to = (from + 1).coerceAtMost(pages.lastIndex)
    val fraction = (position - from).coerceIn(0f, 1f)
    val morph = remember(from, to) { Morph(pageShapes[from], pageShapes[to]) }
    val page = pages[if (fraction < 0.5f) from else to]

    val color = MaterialTheme.colorScheme.primaryContainer
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        // Rotated as a layer rather than in the outline, so corners can swing past the box instead of being cut off.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = position * 45f }
                .clip(MorphShape(morph, fraction))
                .background(color),
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() },
            label = "heroIcon",
        ) { shown ->
            Icon(shown.icon, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun PageContent(index: Int) {
    val page = pages[index]
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        when (index) {
            1 -> NotificationPermission()
            2 -> LookChoices()
            3 -> SoundChoices()
        }
    }
}

@Composable
private fun NotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    FilledTonalButton(
        onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        enabled = !granted,
        shapes = ButtonDefaults.shapes(),
    ) {
        if (granted) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.onboarding_notifications_granted))
        } else {
            Text(stringResource(R.string.onboarding_notifications_allow))
        }
    }
}

@Composable
private fun LookChoices() {
    val settings = LocalContext.current.container.settings
    val prefs by settings.prefs.collectAsStateWithLifecycle()
    Choices(
        options = ThemeMode.entries,
        selected = prefs.themeMode,
        label = {
            when (it) {
                ThemeMode.System -> R.string.theme_system
                ThemeMode.Light -> R.string.theme_light
                ThemeMode.Dark -> R.string.theme_dark
            }
        },
        onSelect = { mode -> settings.update { it.copy(themeMode = mode) } },
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_artwork_colors), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = prefs.artworkColors, onCheckedChange = { on -> settings.update { it.copy(artworkColors = on) } })
    }
}

@Composable
private fun SoundChoices() {
    val settings = LocalContext.current.container.settings
    val prefs by settings.prefs.collectAsStateWithLifecycle()
    Choices(
        options = AudioQuality.entries,
        selected = prefs.audioQuality,
        label = {
            when (it) {
                AudioQuality.High -> R.string.quality_high
                AudioQuality.Normal -> R.string.quality_normal
                AudioQuality.Low -> R.string.quality_low
            }
        },
        onSelect = { quality -> settings.update { it.copy(audioQuality = quality) } },
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.settings_normalize), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = prefs.normalizeVolume, onCheckedChange = { on -> settings.update { it.copy(normalizeVolume = on) } })
    }
}

@Composable
private fun <T> Choices(options: List<T>, selected: T, label: (T) -> Int, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            ToggleButton(
                checked = option == selected,
                onCheckedChange = { onSelect(option) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(label(option)), maxLines = 1)
            }
        }
    }
}

@Composable
private fun Dots(current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        pages.indices.forEach { index ->
            val active = index == current
            val width by animateDpAsState(if (active) 24.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "dotColor",
            )
            Box(Modifier.size(width, 8.dp).clip(CircleShape).background(color))
        }
    }
}
