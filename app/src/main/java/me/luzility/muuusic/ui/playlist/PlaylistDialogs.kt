package me.luzility.muuusic.ui.playlist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.luzility.muuusic.R
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.ui.components.Artwork

@Composable
fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ImportPlaylistDialog(onDismiss: () -> Unit, vm: PlaylistsViewModel = viewModel()) {
    val state by vm.import.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state) {
        val done = state as? ImportState.Done ?: return@LaunchedEffect
        Toast.makeText(context, context.resources.getQuantityString(R.plurals.imported_playlist, done.count, done.name, done.count), Toast.LENGTH_SHORT).show()
        vm.resetImport()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = { vm.resetImport(); onDismiss() },
        title = { Text(stringResource(R.string.import_from_link)) },
        text = {
            Column {
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; if (state != ImportState.Working) vm.resetImport() },
                    label = { Text(stringResource(R.string.import_link_hint)) },
                    singleLine = true,
                    enabled = state != ImportState.Working,
                    isError = state == ImportState.InvalidLink || state == ImportState.Failed,
                    supportingText = {
                        when (state) {
                            ImportState.InvalidLink -> Text(stringResource(R.string.import_invalid))
                            ImportState.Failed -> Text(stringResource(R.string.error_generic))
                            else -> {}
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state == ImportState.Working) {
                    LoadingIndicator(Modifier.padding(top = 8.dp).size(40.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.importFromLink(link) }, enabled = link.isNotBlank() && state != ImportState.Working) {
                Text(stringResource(R.string.import_action))
            }
        },
        dismissButton = { TextButton(onClick = { vm.resetImport(); onDismiss() }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun AddToPlaylistSheet(songs: List<Song>, onDismiss: () -> Unit, vm: PlaylistsViewModel = viewModel()) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var creating by rememberSaveable { mutableStateOf(false) }

    fun confirm(name: String) {
        Toast.makeText(context, context.getString(R.string.added_to_playlist, name), Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.add_to_playlist),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                ListItem(
                    modifier = Modifier.clickable { creating = true },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Icon(Icons.Rounded.Add, null, Modifier.size(56.dp).padding(14.dp)) },
                    headlineContent = { Text(stringResource(R.string.new_playlist)) },
                )
            }
            items(playlists, key = { it.id }) { playlist ->
                ListItem(
                    modifier = Modifier.clickable {
                        vm.addSongs(playlist.id, songs)
                        confirm(playlist.name)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { Artwork(playlist.thumbnailUrl, Modifier.size(56.dp), MaterialTheme.shapes.medium) },
                    headlineContent = { Text(playlist.name) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.song_count, playlist.songCount, playlist.songCount))
                    },
                )
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = stringResource(R.string.new_playlist),
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                vm.create(name, songs)
                creating = false
                confirm(name)
            },
            onDismiss = { creating = false },
        )
    }
}
