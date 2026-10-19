package me.luzility.muuusic.ui.components

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.download.DownloadStatus
import me.luzility.muuusic.data.download.Downloads
import me.luzility.muuusic.data.model.Song

@Composable
fun rememberDownloads(): Downloads {
    val context = LocalContext.current
    return remember(context) { context.container.downloads }
}

@Composable
fun downloadStatuses(): Map<String, DownloadStatus> {
    val statuses by rememberDownloads().statuses.collectAsStateWithLifecycle()
    return statuses
}

@Composable
fun DownloadBadge(status: DownloadStatus?, modifier: Modifier = Modifier) {
    when (status?.state) {
        null -> Unit
        DownloadStatus.State.Done ->
            Icon(Icons.Rounded.DownloadDone, null, tint = MaterialTheme.colorScheme.primary, modifier = modifier.size(16.dp))
        DownloadStatus.State.Failed ->
            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = modifier.size(16.dp))
        else -> CircularProgressIndicator(
            progress = { status.percent / 100f },
            modifier = modifier.size(14.dp),
            strokeWidth = 2.dp,
        )
    }
}

@Composable
fun DownloadAllButton(songs: List<Song>, modifier: Modifier = Modifier) {
    val downloads = rememberDownloads()
    val statuses = downloadStatuses()
    val inList = songs.mapNotNull { statuses[it.id] }
    val done = songs.isNotEmpty() && inList.count { it.state == DownloadStatus.State.Done } == songs.size
    val active = inList.any { it.state == DownloadStatus.State.Downloading || it.state == DownloadStatus.State.Queued }

    val downloadingLabel = stringResource(R.string.downloading)
    FilledTonalIconButton(
        onClick = { if (!done) downloads.download(songs) },
        shapes = IconButtonDefaults.shapes(),
        modifier = modifier,
    ) {
        when {
            done -> Icon(Icons.Rounded.DownloadDone, stringResource(R.string.all_downloaded))
            active -> CircularProgressIndicator(
                progress = { inList.sumOf { it.percent.toDouble() }.toFloat() / (songs.size * 100f) },
                modifier = Modifier.size(20.dp).semantics { contentDescription = downloadingLabel },
                strokeWidth = 2.5.dp,
            )
            else -> Icon(Icons.Rounded.Download, stringResource(R.string.download_all))
        }
    }
}
