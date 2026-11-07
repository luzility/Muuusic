package me.luzility.muuusic.data.account

import android.content.Context
import android.webkit.CookieManager
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.innertube.InnerTubeClient
import me.luzility.muuusic.data.storage.KeystoreCipher
import org.json.JSONObject

class YouTubeAccount(
    context: Context,
    private val innerTube: InnerTubeClient,
    private val importer: YouTubeImport,
    private val scope: CoroutineScope,
) {
    sealed interface ImportState {
        data object Idle : ImportState
        data class Running(val progress: YouTubeImport.Progress) : ImportState
        data class Done(val result: YouTubeImport.Result) : ImportState
        data object Failed : ImportState
    }

    private val store = context.getSharedPreferences("youtube_account", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher(ALIAS)
    private var cookie: String? = null
    private var importJob: Job? = null

    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name.asStateFlow()

    private val _photo = MutableStateFlow<String?>(null)
    val photo: StateFlow<String?> = _photo.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    init {
        val saved = store.getString(KEY_ACCOUNT, null)?.let(cipher::open)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (saved != null) {
            cookie = saved.optString("cookie").takeIf { it.isNotBlank() }
            _name.value = saved.optString("name").takeIf { it.isNotBlank() }
            _photo.value = saved.optString("photo").takeIf { it.isNotBlank() }
            _signedIn.value = cookie != null
        }
        if (_signedIn.value && _photo.value == null) refreshInfo()
    }

    fun signIn(newCookie: String) {
        cookie = newCookie
        _signedIn.value = true
        save()
        refreshInfo()
        importNow()
    }

    private fun refreshInfo() {
        val current = cookie ?: return
        scope.launch {
            val info = runCatching { innerTube.account(current) }.getOrNull() ?: return@launch
            _name.value = info.name
            _photo.value = info.photoUrl
            save()
        }
    }

    fun importNow() {
        val current = cookie ?: return
        if (importJob?.isActive == true) return
        importJob = scope.launch {
            _importState.value = ImportState.Running(YouTubeImport.Progress.Likes)
            _importState.value = runCatching {
                importer.run(current) { progress -> _importState.value = ImportState.Running(progress) }
            }.fold(onSuccess = { ImportState.Done(it) }, onFailure = { ImportState.Failed })
        }
    }

    fun signOut() {
        importJob?.cancel()
        cookie = null
        _name.value = null
        _photo.value = null
        _signedIn.value = false
        _importState.value = ImportState.Idle
        store.edit { remove(KEY_ACCOUNT) }
        CookieManager.getInstance().removeAllCookies(null)
    }

    private fun save() {
        val current = cookie ?: return
        val json = JSONObject()
            .put("cookie", current)
            .put("name", _name.value.orEmpty())
            .put("photo", _photo.value.orEmpty())
            .toString()
        store.edit { putString(KEY_ACCOUNT, cipher.seal(json)) }
    }

    private companion object {
        const val KEY_ACCOUNT = "account"
        const val ALIAS = "muuusic_youtube"
    }
}
