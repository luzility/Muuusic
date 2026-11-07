package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import me.luzility.muuusic.data.account.AccountLink
import me.luzility.muuusic.data.account.YouTubeAccount
import me.luzility.muuusic.data.account.YouTubeImport
import me.luzility.muuusic.desktop.AccountLinkReceiver

@Composable
fun ProfileButton(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val account = LocalServices.current.youtubeAccount
    val signedIn by account.signedIn.collectAsState()
    val name by account.name.collectAsState()
    val photo by account.photo.collectAsState()
    val importState by account.importState.collectAsState()
    var open by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    val close = { open = false }

    if (linking) AccountLinkDialog(onDismiss = { linking = false })
    if (browsing) {
        BrowserLoginWindow(
            onSignedIn = { cookie ->
                browsing = false
                account.signIn(cookie)
            },
            onClose = { browsing = false },
        )
    }

    Box(modifier) {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Avatar(name, photo, 32.dp)
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = close) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2), modifier = Modifier.widthIn(min = 280.dp)) {
                if (signedIn) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name, photo, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name ?: str("youtube_signed_in_unknown"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("YouTube Music", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    DropdownMenuItem(
                        selected = false,
                        onClick = account::importNow,
                        text = { Text(str("youtube_import")) },
                        shapes = MenuDefaults.itemShape(0, 2),
                        leadingIcon = { Icon(Icons.Rounded.CloudDownload, null) },
                        supportingText = { Text(importStatus(importState)) },
                    )
                    DropdownMenuItem(
                        selected = false,
                        onClick = {
                            close()
                            account.signOut()
                        },
                        text = { Text(str("sign_out")) },
                        shapes = MenuDefaults.itemShape(1, 2),
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
                    )
                } else {
                    DropdownMenuItem(
                        selected = false,
                        onClick = {
                            close()
                            linking = true
                        },
                        text = { Text(str("link_title")) },
                        shapes = MenuDefaults.itemShape(0, 2),
                        leadingIcon = { Icon(Icons.Rounded.QrCode2, null) },
                    )
                    DropdownMenuItem(
                        selected = false,
                        onClick = {
                            close()
                            browsing = true
                        },
                        text = { Text(str("browser_sign_in")) },
                        shapes = MenuDefaults.itemShape(1, 2),
                        leadingIcon = { Icon(Icons.Rounded.Login, null) },
                        supportingText = { Text(str("browser_sign_in_summary")) },
                    )
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2), modifier = Modifier.widthIn(min = 280.dp)) {
                DropdownMenuItem(
                    selected = false,
                    onClick = {
                        close()
                        navigator.openSettings("Integrations")
                    },
                    text = { Text(str("settings_integrations")) },
                    shapes = MenuDefaults.itemShape(0, 2),
                    leadingIcon = { Icon(Icons.Rounded.Hub, null) },
                )
                DropdownMenuItem(
                    selected = false,
                    onClick = {
                        close()
                        navigator.openSettings()
                    },
                    text = { Text(str("settings")) },
                    shapes = MenuDefaults.itemShape(1, 2),
                    leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                )
            }
        }
    }
}

@Composable
private fun AccountLinkDialog(onDismiss: () -> Unit) {
    val account = LocalServices.current.youtubeAccount
    val receiver = remember { AccountLinkReceiver() }
    val offer = receiver.offer
    var timedOut by remember { mutableStateOf(false) }
    DisposableEffect(receiver) { onDispose { receiver.close() } }
    LaunchedEffect(receiver) {
        val cookie = receiver.await()
        if (cookie != null) {
            account.signIn(cookie)
            onDismiss()
        } else {
            timedOut = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str("link_title")) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    offer == null -> Text(str("link_no_network"))
                    timedOut -> Text(str("link_timeout"))
                    else -> {
                        Surface(color = Color.White, shape = MaterialTheme.shapes.large) {
                            QrCode(remember(offer) { qrMatrix(AccountLink.uri(offer)) }, Modifier.padding(12.dp).size(220.dp))
                        }
                        Text(str("link_hint"), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str("close")) } },
    )
}

@Composable
private fun QrCode(matrix: BitMatrix, modifier: Modifier) {
    Canvas(modifier) {
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private fun qrMatrix(text: String): BitMatrix =
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))

@Composable
private fun importStatus(state: YouTubeAccount.ImportState): String = when (state) {
    YouTubeAccount.ImportState.Idle -> str("youtube_import_summary")
    YouTubeAccount.ImportState.Failed -> str("youtube_import_failed")
    is YouTubeAccount.ImportState.Done -> str("youtube_import_done", state.result.likes, state.result.playlists)
    is YouTubeAccount.ImportState.Running -> when (val progress = state.progress) {
        YouTubeImport.Progress.Likes -> str("youtube_import_likes")
        is YouTubeImport.Progress.Playlist -> str("youtube_import_playlists", progress.index, progress.total)
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
            else -> Icon(Icons.Rounded.Person, str("settings"), Modifier.size(size * 0.6f), tint = colors.onSecondaryContainer)
        }
    }
}
