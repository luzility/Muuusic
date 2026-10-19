package me.luzility.muuusic.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.settings.Hint

/** Shows a one-time tip next to [content] the first time it is on screen with [enabled] set. */
@Composable
fun HintAnchor(
    hint: Hint,
    title: Int,
    text: Int,
    enabled: Boolean = true,
    above: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val container = LocalContext.current.container
    val seen by container.hints.seen.collectAsStateWithLifecycle()
    val prefs by container.settings.prefs.collectAsStateWithLifecycle()
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val show = enabled && prefs.onboarded && hint.name !in seen

    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        delay(SHOW_DELAY_MS)
        // show() suspends for as long as a persistent tooltip is up, so watch visibility separately.
        launch { state.show() }
        snapshotFlow { state.isVisible }.first { it }
        // However it goes away (button or a tap elsewhere), it counts as seen.
        snapshotFlow { state.isVisible }.first { !it }
        container.hints.markSeen(hint)
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (above) TooltipAnchorPosition.Above else TooltipAnchorPosition.Below,
        ),
        tooltip = {
            RichTooltip(
                title = { Text(stringResource(title)) },
                action = {
                    TextButton(onClick = { scope.launch { state.dismiss() } }) { Text(stringResource(R.string.hint_got_it)) }
                },
            ) { Text(stringResource(text)) }
        },
        state = state,
        modifier = modifier,
        enableUserInput = false,
        content = content,
    )
}

private const val SHOW_DELAY_MS = 700L
