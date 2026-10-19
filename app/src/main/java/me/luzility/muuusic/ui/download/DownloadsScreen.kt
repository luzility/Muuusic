package me.luzility.muuusic.ui.download

import android.text.format.Formatter
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.luzility.muuusic.R
import me.luzility.muuusic.data.download.DownloadStatus
import me.luzility.muuusic.ui.components.EmptyState
import me.luzility.muuusic.ui.components.LocalMiniPlayerInset
import me.luzility.muuusic.ui.components.LocalPlayer
import me.luzility.muuusic.ui.components.SongListItem
import me.luzility.muuusic.ui.components.rememberDownloads
import me.luzility.muuusic.ui.library.PlayButtons

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val player = LocalPlayer.current
    val context = LocalContext.current
    val downloads = rememberDownloads()
    val statuses by downloads.statuses.collectAsStateWithLifecycle()
    val songs by downloads.songs.collectAsStateWithLifecycle(initialValue = emptyList())
    val currentId = player.state.collectAsStateWithLifecycle().value.current?.id
    val pending = statuses.values.filter { it.state != DownloadStatus.State.Done }.sortedBy { it.updatedAt }

    var bytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(songs.size) { bytes = withContext(Dispatchers.IO) { downloads.bytesUsed() } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp + LocalMiniPlayerInset.current)) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            FilledTonalIconButton(
                onClick = onBack,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 12.dp, top = 8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back))
            }
        }
        item {
            Text(
                stringResource(R.string.downloads),
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
            )
            Text(
                pluralStringResource(R.plurals.song_count, songs.size, songs.size) + " • " +
                    stringResource(R.string.downloads_count, Formatter.formatShortFileSize(context, bytes)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            )
        }

        if (songs.isEmpty() && pending.isEmpty()) {
            item { EmptyState(Icons.Rounded.DownloadDone, stringResource(R.string.empty_downloads)) }
        }
        if (songs.isNotEmpty()) {
            item { PlayButtons(onPlay = { player.play(songs) }, onShuffle = { player.play(songs, shuffle = true) }) }
        }
        items(pending, key = { "pending-${it.song.id}" }) { status ->
            SongListItem(status.song, onClick = {}, isCurrent = false)
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongListItem(song, isCurrent = song.id == currentId, onClick = { player.play(songs, index) })
        }
    }
}
