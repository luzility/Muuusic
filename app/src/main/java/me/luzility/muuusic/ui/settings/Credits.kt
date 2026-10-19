package me.luzility.muuusic.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.R

class Credit(val name: String, val use: Int, val license: String, val url: String)

val Credits = listOf(
    Credit("NewPipe Extractor", R.string.credit_newpipe, "GPL-3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    Credit("AndroidX Media3", R.string.credit_media3, "Apache-2.0", "https://github.com/androidx/media"),
    Credit("Jetpack Compose, Material 3", R.string.credit_compose, "Apache-2.0", "https://developer.android.com/compose"),
    Credit("Jetpack Glance", R.string.credit_glance, "Apache-2.0", "https://developer.android.com/develop/ui/compose/glance"),
    Credit("Room", R.string.credit_room, "Apache-2.0", "https://developer.android.com/training/data-storage/room"),
    Credit("MaterialKolor", R.string.credit_materialkolor, "MIT", "https://github.com/jordond/MaterialKolor"),
    Credit("Coil", R.string.credit_coil, "Apache-2.0", "https://github.com/coil-kt/coil"),
    Credit("OkHttp", R.string.credit_okhttp, "Apache-2.0", "https://github.com/square/okhttp"),
    Credit("Reorderable", R.string.credit_reorderable, "Apache-2.0", "https://github.com/Calvin-LL/Reorderable"),
    Credit("Roboto Flex", R.string.credit_font, "OFL-1.1", "https://github.com/googlefonts/roboto-flex"),
    Credit("LRCLIB", R.string.credit_lrclib, "API", "https://lrclib.net"),
    Credit("SponsorBlock", R.string.credit_sponsorblock, "CC BY-NC-SA 4.0", "https://sponsor.ajay.app"),
)

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_credits)) },
        text = {
            LazyColumn {
                items(Credits, key = { it.name }) { credit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(credit.url) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(credit.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${stringResource(credit.use)} · ${credit.license}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
