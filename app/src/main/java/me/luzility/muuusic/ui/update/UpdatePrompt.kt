package me.luzility.muuusic.ui.update

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luzility.muuusic.BuildConfig
import me.luzility.muuusic.R
import me.luzility.muuusic.container
import me.luzility.muuusic.data.update.AppRelease

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
    data class Available(val release: AppRelease) : UpdateCheck
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val updates = app.container.updates
    private val settings = app.container.settings

    private val _check = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val check = _check.asStateFlow()

    /** Release shown in the dialog, if any. */
    private val _prompt = MutableStateFlow<AppRelease?>(null)
    val prompt = _prompt.asStateFlow()

    private var promptIsAutomatic = false

    init {
        if (updates.dueForAutoCheck()) {
            viewModelScope.launch {
                val release = runCatching { updates.newerThan(BuildConfig.VERSION_NAME, settings.prefs.value.updateChannel) }.getOrNull()
                if (release != null) {
                    _check.value = UpdateCheck.Available(release)
                    if (release.version != updates.skipped) {
                        promptIsAutomatic = true
                        _prompt.value = release
                    }
                }
            }
        }
    }

    fun checkNow() {
        if (_check.value == UpdateCheck.Checking) return
        _check.value = UpdateCheck.Checking
        viewModelScope.launch {
            _check.value = runCatching { updates.newerThan(BuildConfig.VERSION_NAME, settings.prefs.value.updateChannel) }
                .fold(
                    onSuccess = { release -> release?.let(UpdateCheck::Available) ?: UpdateCheck.UpToDate },
                    onFailure = { UpdateCheck.Failed },
                )
            (_check.value as? UpdateCheck.Available)?.let {
                promptIsAutomatic = false
                _prompt.value = it.release
            }
        }
    }

    fun show(release: AppRelease) {
        promptIsAutomatic = false
        _prompt.value = release
    }

    fun dismiss() {
        // A prompt that came up on its own doesn't come back for the same version.
        if (promptIsAutomatic) updates.skipped = _prompt.value?.version
        _prompt.value = null
    }
}

/** One instance for the whole activity, so Settings sees what the startup check found. */
@Composable
fun updateViewModel(): UpdateViewModel = viewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity)

@Composable
fun UpdatePrompt(vm: UpdateViewModel = updateViewModel()) {
    val release by vm.prompt.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val shown = release ?: return
    AlertDialog(
        onDismissRequest = vm::dismiss,
        title = {
            // The version name already says when it's a beta.
            Text(stringResource(R.string.update_available, shown.version))
        },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(
                    shown.notes.ifBlank { stringResource(R.string.update_no_notes) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                uriHandler.openUri(shown.apkUrl ?: shown.pageUrl)
                vm.dismiss()
            }) { Text(stringResource(R.string.update_download)) }
        },
        dismissButton = {
            TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.update_later)) }
        },
    )
}
