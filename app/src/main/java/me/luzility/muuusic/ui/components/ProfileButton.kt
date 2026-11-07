package me.luzility.muuusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import me.luzility.muuusic.R
import me.luzility.muuusic.container

@Composable
fun ProfileButton(modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current
    val account = LocalContext.current.container.youtubeAccount
    val name by account.name.collectAsStateWithLifecycle()
    val photo by account.photo.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.settings)
    IconButton(onClick = navigator.openSettings, shapes = IconButtonDefaults.shapes(), modifier = modifier.semantics { contentDescription = label }) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(colors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val initial = name?.trim()?.firstOrNull()?.uppercase()
            when {
                photo != null -> AsyncImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                initial != null -> Text(initial, style = MaterialTheme.typography.titleSmall, color = colors.onSecondaryContainer)
                else -> Icon(Icons.Rounded.Person, null, Modifier.size(20.dp), tint = colors.onSecondaryContainer)
            }
        }
    }
}
