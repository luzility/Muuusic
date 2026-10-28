package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.model.Song

@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val services = LocalServices.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val playlists by services.playlists.playlists.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameDialog(
            title = str("new_playlist"),
            confirm = str("create"),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                scope.launch {
                    services.playlists.create(name, songs)
                    snackbar.showSnackbar(str("added_to_playlist", name))
                }
                onDismiss()
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("add_to_playlist")) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { creating = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(16.dp))
                        Text(str("new_playlist"), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                            scope.launch {
                                services.playlists.addSongs(playlist.id, songs)
                                snackbar.showSnackbar(str("added_to_playlist", playlist.name))
                            }
                            onDismiss()
                        }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(playlist.thumbnailUrl, Modifier.size(40.dp), MaterialTheme.shapes.small)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                            Text(Strings.plural("song_count", playlist.songCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(str("cancel")) } },
    )
}

@Composable
fun NameDialog(title: String, confirm: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(str("playlist_name")) }, singleLine = true, modifier = Modifier.typingField())
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str("cancel")) } },
    )
}
