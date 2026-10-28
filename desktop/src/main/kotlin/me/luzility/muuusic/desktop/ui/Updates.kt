package me.luzility.muuusic.desktop.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.luzility.muuusic.AppInfo
import me.luzility.muuusic.data.update.AppRelease
import me.luzility.muuusic.desktop.DesktopServices

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
}

sealed interface UpdateInstall {
    data object Idle : UpdateInstall
    data class Downloading(val progress: Float) : UpdateInstall
    data object Failed : UpdateInstall
}

val LocalUpdates = staticCompositionLocalOf<UpdateModel> { error("UpdateModel not provided") }

class UpdateModel(private val services: DesktopServices, private val scope: CoroutineScope, private val onRestart: () -> Unit) {
    private val updates = services.updates
    private val updater = services.updater

    var check by mutableStateOf<UpdateCheck>(UpdateCheck.Idle)
        private set
    var prompt by mutableStateOf<AppRelease?>(null)
        private set
    var installing by mutableStateOf<UpdateInstall>(UpdateInstall.Idle)
        private set
    private var promptIsAutomatic = false

    init {
        if (updates.dueForAutoCheck()) {
            scope.launch {
                val release = runCatching { updates.newerThan(AppInfo.versionName, services.settings.prefs.value.updateChannel) }.getOrNull()
                if (release != null) {
                    check = UpdateCheck.Available(release)
                    if (release.version != updates.skipped) {
                        promptIsAutomatic = true
                        prompt = release
                    }
                }
            }
        }
    }

    fun canInstall(release: AppRelease) = updater.assetFor(release) != null

    fun checkNow() {
        if (check == UpdateCheck.Checking) return
        check = UpdateCheck.Checking
        scope.launch {
            check = runCatching { updates.newerThan(AppInfo.versionName, services.settings.prefs.value.updateChannel) }.fold(
                onSuccess = { release -> release?.let(UpdateCheck::Available) ?: UpdateCheck.UpToDate },
                onFailure = { UpdateCheck.Failed },
            )
            (check as? UpdateCheck.Available)?.let {
                promptIsAutomatic = false
                prompt = it.release
            }
        }
    }

    fun show(release: AppRelease) {
        promptIsAutomatic = false
        prompt = release
    }

    fun dismiss() {
        if (installing is UpdateInstall.Downloading) return
        if (promptIsAutomatic) updates.skipped = prompt?.version
        prompt = null
        installing = UpdateInstall.Idle
    }

    fun install(release: AppRelease) {
        val url = updater.assetFor(release) ?: return
        if (installing is UpdateInstall.Downloading) return
        installing = UpdateInstall.Downloading(0f)
        scope.launch {
            val done = runCatching { updater.install(url) { progress -> scope.launch { installing = UpdateInstall.Downloading(progress) } } }.getOrDefault(false)
            if (done) {
                onRestart()
            } else {
                installing = UpdateInstall.Failed
            }
        }
    }
}

@Composable
fun UpdatePrompt() {
    val model = LocalUpdates.current
    val uri = LocalUriHandler.current
    val shown = model.prompt ?: return
    val state = model.installing
    val installable = model.canInstall(shown)
    AlertDialog(
        onDismissRequest = model::dismiss,
        title = { Text(str("update_available", shown.version)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(shown.notes.ifBlank { str("update_no_notes") }, style = MaterialTheme.typography.bodyMedium)
                when (state) {
                    is UpdateInstall.Downloading -> {
                        Spacer(Modifier.height(20.dp))
                        Text(str("update_installing", (state.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        LinearWavyProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    UpdateInstall.Failed -> {
                        Spacer(Modifier.height(16.dp))
                        Text(str("update_install_failed"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    }
                    UpdateInstall.Idle -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (installable && state != UpdateInstall.Failed) {
                        model.install(shown)
                    } else {
                        uri.openUri(shown.pageUrl)
                        model.dismiss()
                    }
                },
                enabled = state !is UpdateInstall.Downloading,
            ) {
                Text(str(if (installable && state != UpdateInstall.Failed) "update_install" else "update_download"))
            }
        },
        dismissButton = {
            TextButton(onClick = model::dismiss, enabled = state !is UpdateInstall.Downloading) { Text(str("update_later")) }
        },
    )
}
