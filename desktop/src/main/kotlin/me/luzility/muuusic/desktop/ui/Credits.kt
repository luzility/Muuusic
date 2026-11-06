package me.luzility.muuusic.desktop.ui

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
import androidx.compose.ui.unit.dp

class Credit(val name: String, val use: String, val license: String, val url: String)

val Credits = listOf(
    Credit("NewPipe Extractor", "credit_newpipe", "GPL-3.0", "https://github.com/TeamNewPipe/NewPipeExtractor"),
    Credit("mpv", "credit_media3", "GPL-2.0+", "https://mpv.io"),
    Credit("Compose Multiplatform, Material 3", "credit_compose", "Apache-2.0", "https://www.jetbrains.com/compose-multiplatform"),
    Credit("Room", "credit_room", "Apache-2.0", "https://developer.android.com/training/data-storage/room"),
    Credit("MaterialKolor", "credit_materialkolor", "MIT", "https://github.com/jordond/MaterialKolor"),
    Credit("Coil", "credit_coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
    Credit("OkHttp", "credit_okhttp", "Apache-2.0", "https://github.com/square/okhttp"),
    Credit("JNA", "credit_jna", "Apache-2.0", "https://github.com/java-native-access/jna"),
    Credit("dbus-java", "credit_dbus", "MIT", "https://github.com/hypfvieh/dbus-java"),
    Credit("Roboto Flex", "credit_font", "OFL-1.1", "https://github.com/googlefonts/roboto-flex"),
    Credit("LRCLIB", "credit_lrclib", "API", "https://lrclib.net"),
    Credit("SponsorBlock", "credit_sponsorblock", "CC BY-NC-SA 4.0", "https://sponsor.ajay.app"),
)

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("settings_credits")) },
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
                            "${str(credit.use)} · ${credit.license}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("close")) } },
    )
}
