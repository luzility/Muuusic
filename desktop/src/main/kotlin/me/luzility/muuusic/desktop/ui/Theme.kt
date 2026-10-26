package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.luzility.muuusic.data.settings.AppPrefs
import me.luzility.muuusic.data.settings.ThemeMode
import kotlin.math.roundToInt

val BrandSeed = Color(0xFF7C4DFF)

object FlexFont {
    private val data: ByteArray by lazy { FlexFont::class.java.getResourceAsStream("/fonts/roboto_flex.ttf")!!.use { it.readBytes() } }
    private val cache = HashMap<Int, FontFamily>()

    fun quantize(weight: Float): Int = ((weight.coerceIn(100f, 1000f) / 10f).roundToInt() * 10)

    fun font(weight: Int) = Font(
        identity = "RobotoFlex-$weight",
        data = data,
        weight = FontWeight(weight),
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

    fun family(weight: Int): FontFamily = synchronized(cache) { cache.getOrPut(weight) { FontFamily(font(weight)) } }

    val all: FontFamily by lazy { FontFamily((100..1000 step 100).map(::font)) }
}

private val artworkSeeds = object : LinkedHashMap<String, Color>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 50
}

@Composable
fun MuuusicDesktopTheme(artworkUrl: String?, prefs: AppPrefs, content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val seedUrl = artworkUrl.takeIf { prefs.artworkColors }
    val artworkSeed by produceState(initialValue = seedUrl?.let { artworkSeeds[it] }, seedUrl) {
        value = seedUrl?.let { url -> artworkSeeds[url] ?: seedFromArtwork(url)?.also { artworkSeeds[url] = it } }
    }
    DynamicMaterialExpressiveTheme(
        seedColor = artworkSeed ?: BrandSeed,
        motionScheme = MotionScheme.expressive(),
        isDark = dark,
        isAmoled = dark && prefs.pureBlack,
        style = PaletteStyle.TonalSpot,
        typography = flexTypography(MaterialTheme.typography),
        animate = true,
        content = content,
    )
}

private fun flexTypography(base: Typography): Typography {
    fun TextStyle.flex() = copy(fontFamily = FlexFont.all)
    return Typography(
        displayLarge = base.displayLarge.flex(),
        displayMedium = base.displayMedium.flex(),
        displaySmall = base.displaySmall.flex(),
        headlineLarge = base.headlineLarge.flex(),
        headlineMedium = base.headlineMedium.flex(),
        headlineSmall = base.headlineSmall.flex(),
        titleLarge = base.titleLarge.flex(),
        titleMedium = base.titleMedium.flex(),
        titleSmall = base.titleSmall.flex(),
        bodyLarge = base.bodyLarge.flex(),
        bodyMedium = base.bodyMedium.flex(),
        bodySmall = base.bodySmall.flex(),
        labelLarge = base.labelLarge.flex(),
        labelMedium = base.labelMedium.flex(),
        labelSmall = base.labelSmall.flex(),
        displayLargeEmphasized = base.displayLargeEmphasized.flex(),
        displayMediumEmphasized = base.displayMediumEmphasized.flex(),
        displaySmallEmphasized = base.displaySmallEmphasized.flex(),
        headlineLargeEmphasized = base.headlineLargeEmphasized.flex(),
        headlineMediumEmphasized = base.headlineMediumEmphasized.flex(),
        headlineSmallEmphasized = base.headlineSmallEmphasized.flex(),
        titleLargeEmphasized = base.titleLargeEmphasized.flex(),
        titleMediumEmphasized = base.titleMediumEmphasized.flex(),
        titleSmallEmphasized = base.titleSmallEmphasized.flex(),
        bodyLargeEmphasized = base.bodyLargeEmphasized.flex(),
        bodyMediumEmphasized = base.bodyMediumEmphasized.flex(),
        bodySmallEmphasized = base.bodySmallEmphasized.flex(),
        labelLargeEmphasized = base.labelLargeEmphasized.flex(),
        labelMediumEmphasized = base.labelMediumEmphasized.flex(),
        labelSmallEmphasized = base.labelSmallEmphasized.flex(),
    )
}

private suspend fun seedFromArtwork(url: String): Color? = withContext(Dispatchers.Default) {
    val context = PlatformContext.INSTANCE
    val request = ImageRequest.Builder(context).data(url).size(128).build()
    val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult ?: return@withContext null
    result.image.toBitmap().asComposeImageBitmap().themeColorOrNull()
}
