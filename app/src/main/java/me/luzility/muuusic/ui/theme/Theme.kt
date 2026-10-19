package me.luzility.muuusic.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import me.luzility.muuusic.data.settings.AppPrefs
import me.luzility.muuusic.data.settings.ThemeMode
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BrandSeed = Color(0xFF7C4DFF)

private val artworkSeeds = object : LinkedHashMap<String, Color>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 50
}

@Composable
fun MuuusicTheme(artworkUrl: String?, prefs: AppPrefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val view = LocalView.current
    // Keep status/navigation bar icons readable when the app theme differs from the system's.
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val fallbackSeed = remember(dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
        } else {
            BrandSeed
        }
    }
    val seedUrl = artworkUrl.takeIf { prefs.artworkColors }
    val artworkSeed by produceState(initialValue = seedUrl?.let { artworkSeeds[it] }, seedUrl) {
        if (seedUrl == null) {
            value = null
            return@produceState
        }
        val seed = artworkSeeds[seedUrl] ?: seedFromArtwork(context, seedUrl)?.also { artworkSeeds[seedUrl] = it }
        value = seed
    }

    DynamicMaterialExpressiveTheme(
        seedColor = artworkSeed ?: fallbackSeed,
        motionScheme = MotionScheme.expressive(),
        isDark = dark,
        isAmoled = dark && prefs.pureBlack,
        style = PaletteStyle.TonalSpot,
        animate = true,
        content = content,
    )
}

private suspend fun seedFromArtwork(context: Context, url: String): Color? = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(128)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
    result.image.toBitmap().asImageBitmap().themeColorOrNull()
}
