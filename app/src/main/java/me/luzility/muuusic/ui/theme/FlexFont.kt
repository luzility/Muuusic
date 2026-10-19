package me.luzility.muuusic.ui.theme

import android.content.Context
import android.content.res.AssetManager
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

object FlexFont {
    private val cache = HashMap<Int, FontFamily>()
    private lateinit var assets: AssetManager

    // Asset, not a res/font: MIUI swaps resource fonts for its own, which kills the weight morph.
    fun init(context: Context) {
        assets = context.applicationContext.assets
    }

    // Rounded to steps of 10 so an animation reuses a few dozen typefaces.
    fun quantize(weight: Float): Int = ((weight.coerceIn(100f, 1000f) / 10f).roundToInt() * 10)

    @OptIn(ExperimentalTextApi::class)
    fun family(weight: Int): FontFamily = cache.getOrPut(weight) {
        FontFamily(
            Font(
                path = "fonts/roboto_flex.ttf",
                assetManager = assets,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        )
    }
}
