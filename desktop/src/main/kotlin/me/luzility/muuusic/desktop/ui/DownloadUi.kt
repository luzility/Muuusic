package me.luzility.muuusic.desktop.ui

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.desktop.DownloadStatus

@Composable
fun downloadStatuses(): Map<String, DownloadStatus> {
    val statuses by LocalServices.current.downloads.statuses.collectAsState()
    return statuses
}

@Composable
fun DownloadBadge(status: DownloadStatus?, modifier: Modifier = Modifier) {
    when (status?.state) {
        null -> Unit
        DownloadStatus.State.Done -> Icon(Icons.Rounded.DownloadDone, null, tint = MaterialTheme.colorScheme.primary, modifier = modifier.size(16.dp))
        DownloadStatus.State.Failed -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = modifier.size(16.dp))
        else -> CircularProgressIndicator(progress = { status.percent / 100f }, modifier = modifier.size(14.dp), strokeWidth = 2.dp)
    }
}

@Composable
fun DownloadAllButton(songs: List<Song>, modifier: Modifier = Modifier) {
    val downloads = LocalServices.current.downloads
    val statuses = downloadStatuses()
    val inList = songs.mapNotNull { statuses[it.id] }
    val done = songs.isNotEmpty() && inList.count { it.state == DownloadStatus.State.Done } == songs.size
    val active = inList.any { it.state == DownloadStatus.State.Downloading || it.state == DownloadStatus.State.Queued }
    FilledTonalIconButton(onClick = { if (!done) downloads.download(songs) }, shapes = IconButtonDefaults.shapes(), modifier = modifier) {
        when {
            done -> Icon(Icons.Rounded.DownloadDone, str("all_downloaded"))
            active -> CircularProgressIndicator(
                progress = { inList.sumOf { it.percent.toDouble() }.toFloat() / (songs.size * 100f) },
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
            )
            else -> Icon(Icons.Rounded.Download, str("download_all"))
        }
    }
}
