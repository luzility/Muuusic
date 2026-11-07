package me.luzility.muuusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.account.YouTubeAccount
import me.luzility.muuusic.data.account.YouTubeImport

@Composable
fun ProfileButton(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val account = LocalContext.current.container.youtubeAccount
    val signedIn by account.signedIn.collectAsStateWithLifecycle()
    val name by account.name.collectAsStateWithLifecycle()
    val photo by account.photo.collectAsStateWithLifecycle()
    val importState by account.importState.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.settings)
    val close = { open = false }

    Box(modifier) {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.semantics { contentDescription = label }) {
            Avatar(name, photo, 32.dp)
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = close) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2), modifier = Modifier.widthIn(min = 260.dp)) {
                if (signedIn) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name, photo, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                name ?: stringResource(R.string.youtube_signed_in_unknown),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("YouTube Music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    DropdownMenuItem(
                        onClick = account::importNow,
                        text = { Text(stringResource(R.string.youtube_import)) },
                        shape = MenuDefaults.itemShape(0, 2).shape,
                        leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) },
                        supportingText = { Text(importStatus(importState)) },
                    )
                    DropdownMenuItem(
                        onClick = {
                            close()
                            account.signOut()
                        },
                        text = { Text(stringResource(R.string.sign_out)) },
                        shape = MenuDefaults.itemShape(1, 2).shape,
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                    )
                } else {
                    DropdownMenuItem(
                        onClick = {
                            close()
                            navigator.openGoogleLogin()
                        },
                        text = { Text(stringResource(R.string.youtube_sign_in)) },
                        shape = MenuDefaults.itemShape(0, 1).shape,
                        leadingIcon = { Icon(Icons.Rounded.Login, null) },
                    )
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2), modifier = Modifier.widthIn(min = 260.dp)) {
                DropdownMenuItem(
                    onClick = {
                        close()
                        navigator.openIntegrations()
                    },
                    text = { Text(stringResource(R.string.settings_integrations)) },
                    shape = MenuDefaults.itemShape(0, 2).shape,
                    leadingIcon = { Icon(Icons.Rounded.Hub, null) },
                )
                DropdownMenuItem(
                    onClick = {
                        close()
                        navigator.openSettings()
                    },
                    text = { Text(stringResource(R.string.settings)) },
                    shape = MenuDefaults.itemShape(1, 2).shape,
                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                )
            }
        }
    }
}

@Composable
fun importStatus(state: YouTubeAccount.ImportState): String = when (state) {
    YouTubeAccount.ImportState.Idle -> stringResource(R.string.youtube_import_summary)
    YouTubeAccount.ImportState.Failed -> stringResource(R.string.youtube_import_failed)
    is YouTubeAccount.ImportState.Done -> stringResource(R.string.youtube_import_done, state.result.likes, state.result.playlists)
    is YouTubeAccount.ImportState.Running -> when (val progress = state.progress) {
        YouTubeImport.Progress.Likes -> stringResource(R.string.youtube_import_likes)
        is YouTubeImport.Progress.Playlist -> stringResource(R.string.youtube_import_playlists, progress.index, progress.total)
    }
}

@Composable
private fun Avatar(name: String?, photo: String?, size: Dp) {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.size(size).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
        val initial = name?.trim()?.firstOrNull()?.uppercase()
        when {
            photo != null -> AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            initial != null -> Text(initial, style = MaterialTheme.typography.titleSmall, color = colors.onSecondaryContainer)
            else -> Icon(Icons.Rounded.Person, null, Modifier.size(size * 0.6f), tint = colors.onSecondaryContainer)
        }
    }
}
