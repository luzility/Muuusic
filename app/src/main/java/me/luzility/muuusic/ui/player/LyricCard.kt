package me.luzility.muuusic.ui.player

import me.luzility.muuusic.ui.components.tapHaptics
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.luzility.muuusic.R
import me.luzility.muuusic.data.model.Song
import me.luzility.muuusic.ui.components.Artwork
import me.luzility.muuusic.ui.theme.FlexFont
import java.io.File

private enum class CardStyle(val label: Int) { Artwork(R.string.card_style_artwork), Vivid(R.string.card_style_vivid), Dark(R.string.card_style_dark) }

private const val MaxLines = 4

@Composable
fun LyricCardSheet(song: Song, lines: List<String>, initial: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selected = remember { mutableStateListOf(initial.coerceIn(0, (lines.size - 1).coerceAtLeast(0))) }
    var style by remember { mutableStateOf(CardStyle.Artwork) }
    val layer = rememberGraphicsLayer()
    val backdrop = cardColors(style).first

    ModalBottomSheet(modifier = Modifier.tapHaptics(), onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(stringResource(R.string.share_lyrics), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(16.dp))

            LyricCard(
                song = song,
                lines = selected.sorted().mapNotNull { lines.getOrNull(it) },
                style = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            )

            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardStyle.entries.forEach { option ->
                    FilterChip(
                        selected = style == option,
                        onClick = { style = option },
                        label = { Text(stringResource(option.label)) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.lyrics_card_lines, MaxLines),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (initial - 1).coerceAtLeast(0))
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                itemsIndexed(lines) { index, line ->
                    val checked = index in selected
                    val enabled = checked || selected.size < MaxLines
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                if (checked) {
                                    if (selected.size > 1) selected.remove(index)
                                } else {
                                    selected.add(index)
                                }
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                        Spacer(Modifier.width(8.dp))
                        Text(line, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        val bitmap = layer.toImageBitmap().asAndroidBitmap()
                        shareImage(context, bitmap, backdrop, song)
                    }
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Rounded.Share, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.share))
            }
        }
    }
}

@Composable
private fun cardColors(style: CardStyle): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when (style) {
        CardStyle.Artwork -> colors.primaryContainer to colors.onPrimaryContainer
        CardStyle.Vivid -> colors.primary to colors.onPrimary
        CardStyle.Dark -> Color(0xFF141218) to Color.White
    }
}

@Composable
private fun LyricCard(song: Song, lines: List<String>, style: CardStyle, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (targetBackground, targetContent) = cardColors(style)
    val background by animateColorAsState(targetBackground, label = "cardBackground")
    val content by animateColorAsState(targetContent, label = "cardContent")
    val accent = if (style == CardStyle.Dark) colors.inversePrimary else content

    Surface(shape = RoundedCornerShape(32.dp), color = background, contentColor = content, modifier = modifier) {
        Column(Modifier.padding(24.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(song.thumbnailUrl, Modifier.size(56.dp), MaterialShapes.Cookie9Sided.toShape())
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = FlexFont.family(820),
                            fontWeight = FontWeight(820),
                            lineHeight = 32.sp,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp), tint = accent)
                Spacer(Modifier.width(4.dp))
                Text("Muuusic", style = MaterialTheme.typography.labelLarge, color = accent)
            }
        }
    }
}

private suspend fun shareImage(context: Context, card: Bitmap, background: Color, song: Song) {
    val file = withContext(Dispatchers.IO) {
        val margin = card.width / 10
        val image = createBitmap(card.width + margin * 2, card.height + margin * 2)
        android.graphics.Canvas(image).apply {
            drawColor(lerp(background, Color.Black, 0.35f).toArgb())
            drawBitmap(card.copy(Bitmap.Config.ARGB_8888, false), margin.toFloat(), margin.toFloat(), null)
        }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, "lyrics.png").also { file -> file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, "${song.title} - ${song.artist}\nhttps://music.youtube.com/watch?v=${song.id}")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
