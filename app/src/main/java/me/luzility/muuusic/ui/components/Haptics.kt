package me.luzility.muuusic.ui.components

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

enum class Haptic { Tap, Heavy, Like, ToggleOn, ToggleOff, Tick, Land, Rise, Fall, LongPress }

interface Haptics : HapticFeedback {
    fun perform(effect: Haptic)

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = perform(
        when (hapticFeedbackType) {
            HapticFeedbackType.LongPress -> Haptic.LongPress
            HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentFrequentTick, HapticFeedbackType.TextHandleMove -> Haptic.Tick
            HapticFeedbackType.GestureThresholdActivate, HapticFeedbackType.GestureEnd, HapticFeedbackType.Confirm -> Haptic.Land
            HapticFeedbackType.ToggleOn -> Haptic.ToggleOn
            HapticFeedbackType.ToggleOff -> Haptic.ToggleOff
            else -> Haptic.Tap
        },
    )
}

private object NoHaptics : Haptics {
    override fun perform(effect: Haptic) = Unit
}

val LocalHaptics = staticCompositionLocalOf<Haptics> { NoHaptics }

private class RichHaptics(context: Context, private val view: View) : Haptics {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val effects = mutableMapOf<Haptic, VibrationEffect?>()
    var lastExplicitAt = 0L
        private set

    override fun perform(effect: Haptic) {
        if (effect != Haptic.Tap) lastExplicitAt = SystemClock.uptimeMillis()
        val composed = effects.getOrPut(effect) {
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) compose(effect) else null) ?: basic(effect)
        }
        val target = vibrator
        if (composed != null && target != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                target.vibrate(composed, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                target.vibrate(composed)
            }
        } else {
            view.performHapticFeedback(fallback(effect))
        }
    }

    fun tap() {
        if (SystemClock.uptimeMillis() - lastExplicitAt > EXPLICIT_WINDOW_MS) perform(Haptic.Tap)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun compose(effect: Haptic): VibrationEffect? {
        val v = vibrator ?: return null
        fun has(primitive: Int) = v.areAllPrimitivesSupported(primitive)
        val steps: List<Triple<Int, Float, Int>> = when (effect) {
            Haptic.Tap -> listOf(Triple(Composition.PRIMITIVE_CLICK, 0.6f, 0))
            Haptic.Heavy -> listOf(Triple(Composition.PRIMITIVE_THUD, 0.8f, 0), Triple(Composition.PRIMITIVE_CLICK, 0.5f, 0))
            Haptic.Like -> listOf(Triple(Composition.PRIMITIVE_QUICK_RISE, 0.5f, 0), Triple(Composition.PRIMITIVE_CLICK, 1f, 20))
            Haptic.ToggleOn -> listOf(Triple(Composition.PRIMITIVE_QUICK_RISE, 0.4f, 0), Triple(Composition.PRIMITIVE_TICK, 0.9f, 10))
            Haptic.ToggleOff -> listOf(Triple(Composition.PRIMITIVE_QUICK_FALL, 0.4f, 0), Triple(Composition.PRIMITIVE_LOW_TICK, 0.9f, 10))
            Haptic.Tick -> listOf(Triple(Composition.PRIMITIVE_TICK, 0.5f, 0))
            Haptic.Land -> listOf(Triple(Composition.PRIMITIVE_CLICK, 0.9f, 0))
            Haptic.Rise -> listOf(Triple(Composition.PRIMITIVE_QUICK_RISE, 0.35f, 0))
            Haptic.Fall -> listOf(Triple(Composition.PRIMITIVE_QUICK_FALL, 0.35f, 0))
            Haptic.LongPress -> listOf(Triple(Composition.PRIMITIVE_THUD, 0.7f, 0))
        }
        val usable = steps.mapNotNull { (primitive, scale, delay) ->
            when {
                has(primitive) -> Triple(primitive, scale, delay)
                primitive == Composition.PRIMITIVE_THUD && has(Composition.PRIMITIVE_CLICK) -> Triple(Composition.PRIMITIVE_CLICK, 1f, delay)
                primitive == Composition.PRIMITIVE_LOW_TICK && has(Composition.PRIMITIVE_TICK) -> Triple(Composition.PRIMITIVE_TICK, scale * 0.7f, delay)
                else -> null
            }
        }
        if (usable.isEmpty()) return null
        return VibrationEffect.startComposition().apply {
            usable.forEach { (primitive, scale, delay) -> addPrimitive(primitive, scale, delay) }
        }.compose()
    }

    private fun basic(effect: Haptic): VibrationEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val v = vibrator ?: return null
        fun ramp(vararg amplitudes: Int) =
            if (v.hasAmplitudeControl()) VibrationEffect.createWaveform(LongArray(amplitudes.size) { RAMP_STEP_MS }, amplitudes, -1) else null
        return when (effect) {
            Haptic.Tap, Haptic.Tick -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            Haptic.Land -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            Haptic.Heavy, Haptic.LongPress -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            Haptic.Like -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            Haptic.ToggleOn -> ramp(30, 70, 130, 220, 0, 255)
            Haptic.ToggleOff -> ramp(255, 0, 220, 130, 70, 30)
            Haptic.Rise -> ramp(20, 45, 80, 120, 170)
            Haptic.Fall -> ramp(170, 120, 80, 45, 20)
        }
    }

    private fun fallback(effect: Haptic) = when (effect) {
        Haptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.Heavy, Haptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
        Haptic.ToggleOn -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.ToggleOff -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.Rise, Haptic.Fall -> HapticFeedbackConstants.CLOCK_TICK
        else -> HapticFeedbackConstants.CONTEXT_CLICK
    }

    private companion object {
        const val EXPLICIT_WINDOW_MS = 150L
        const val RAMP_STEP_MS = 9L
    }
}

@Composable
fun ProvideHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    val view = LocalView.current
    val rich = remember(view) { RichHaptics(view.context, view) }
    val haptics: Haptics = if (enabled) rich else NoHaptics
    CompositionLocalProvider(LocalHaptics provides haptics, LocalHapticFeedback provides haptics, content = content)
}

@Composable
fun Modifier.tapHaptics(): Modifier {
    val haptics = LocalHaptics.current
    return pointerInput(haptics) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                if (!change.pressed) {
                    val held = change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
                    if (change.isConsumed && !moved && !held) (haptics as? RichHaptics)?.tap()
                    break
                }
            }
        }
    }
}
