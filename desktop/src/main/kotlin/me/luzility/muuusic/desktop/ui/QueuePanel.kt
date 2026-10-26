package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.luzility.muuusic.data.model.Song

val QueuePanelWidth = 360.dp

@Composable
fun QueuePanel(onClose: () -> Unit) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    val order = state.playOrder.filter { it in state.queue.indices }
    val position = order.indexOf(state.currentIndex).coerceAtLeast(0)
    val list = rememberLazyListState()
    LaunchedEffect(state.current?.id) { if (order.isNotEmpty()) list.animateScrollToItem(position) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(QueuePanelWidth).fillMaxHeight()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(str("queue"), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, str("close")) }
            }
            LazyColumn(state = list, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp)) {
                items(order, key = { "${it}_${state.queue[it].id}" }) { index ->
                    QueueRow(
                        song = state.queue[index],
                        current = index == state.currentIndex,
                        played = order.indexOf(index) < position,
                        onClick = { player.skipTo(index) },
                        onRemove = { player.removeAt(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(song: Song, current: Boolean, played: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (current) colors.secondaryContainer else if (hovered) colors.surfaceContainerHigh else colors.surfaceContainerLow)
            .hoverable(interactions)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(song.thumbnailUrl, Modifier.size(44.dp), MaterialTheme.shapes.small)
            if (current) Icon(Icons.Rounded.GraphicEq, null, tint = colors.inverseOnSurface, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (played && !current) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (hovered && !current) {
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, str("remove"), modifier = Modifier.size(18.dp))
            }
        }
    }
}
