package me.luzility.muuusic.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.luzility.muuusic.data.innertube.InnerTubeClient
import me.luzility.muuusic.data.innertube.LibraryPlaylist
import org.json.JSONArray
import org.json.JSONObject

interface SecretStore {
    fun load(): String?
    fun save(value: String)
    fun clear()
}

private const val SYNC_INTERVAL_MS = 60 * 60 * 1000L

class YouTubeAccount(
    private val store: SecretStore,
    private val innerTube: InnerTubeClient,
    private val importer: YouTubeImport,
    private val scope: CoroutineScope,
    private val onSignOut: () -> Unit = {},
) {
    sealed interface ImportState {
        data object Idle : ImportState
        data class Running(val progress: YouTubeImport.Progress) : ImportState
        data class Done(val result: YouTubeImport.Result) : ImportState
        data object Failed : ImportState
    }

    private var cookie: String? = null
    private var importJob: Job? = null
    private var syncedLikes: Set<String>? = null
    private var lastSync = 0L

    private val _name = MutableStateFlow<String?>(null)
    val name: StateFlow<String?> = _name.asStateFlow()

    private val _photo = MutableStateFlow<String?>(null)
    val photo: StateFlow<String?> = _photo.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _playlists = MutableStateFlow<List<LibraryPlaylist>>(emptyList())
    val playlists: StateFlow<List<LibraryPlaylist>> = _playlists.asStateFlow()
    private var playlistsJob: Job? = null

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    init {
        val saved = store.load()?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (saved != null) {
            cookie = saved.optString("cookie").takeIf { it.isNotBlank() }
            _name.value = saved.optString("name").takeIf { it.isNotBlank() }
            _photo.value = saved.optString("photo").takeIf { it.isNotBlank() }
            syncedLikes = saved.optJSONArray("likes")?.let { array -> List(array.length()) { array.getString(it) }.toSet() }
            lastSync = saved.optLong("lastSync")
            _signedIn.value = cookie != null
        }
        if (_signedIn.value && _photo.value == null) refreshInfo()
        if (_signedIn.value) refreshPlaylists()
        scope.launch {
            while (true) {
                if (_signedIn.value && System.currentTimeMillis() - lastSync > SYNC_INTERVAL_MS) syncLikes()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    fun cookie(): String? = cookie

    fun signIn(newCookie: String) {
        cookie = newCookie
        _signedIn.value = true
        save()
        refreshInfo()
        refreshPlaylists()
        importNow()
    }

    fun refreshPlaylists() {
        val current = cookie ?: return
        if (playlistsJob?.isActive == true) return
        playlistsJob = scope.launch {
            runCatching { innerTube.libraryPlaylists(current) }.getOrNull()?.let { _playlists.value = it }
        }
    }

    fun importNow() {
        val current = cookie ?: return
        if (importJob?.isActive == true) return
        importJob = scope.launch {
            _importState.value = ImportState.Running(YouTubeImport.Progress.Likes)
            _importState.value = runCatching {
                importer.run(current) { progress -> _importState.value = ImportState.Running(progress) }
            }.fold(
                onSuccess = {
                    remember(it.likedIds)
                    ImportState.Done(it)
                },
                onFailure = { ImportState.Failed },
            )
        }
    }

    private fun syncLikes() {
        val current = cookie ?: return
        if (importJob?.isActive == true) return
        val known = syncedLikes
        importJob = scope.launch {
            val ids = runCatching {
                if (known == null) importer.likedIds(current) else importer.syncLikes(current, known).likedIds
            }.getOrNull()
            ids?.let(::remember)
        }
    }

    private fun remember(likedIds: Set<String>) {
        syncedLikes = likedIds
        lastSync = System.currentTimeMillis()
        save()
    }

    fun signOut() {
        importJob?.cancel()
        cookie = null
        syncedLikes = null
        lastSync = 0
        _name.value = null
        _photo.value = null
        playlistsJob?.cancel()
        _playlists.value = emptyList()
        _signedIn.value = false
        _importState.value = ImportState.Idle
        store.clear()
        onSignOut()
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

    private fun save() {
        val current = cookie ?: return
        store.save(
            JSONObject()
                .put("cookie", current)
                .put("name", _name.value.orEmpty())
                .put("photo", _photo.value.orEmpty())
                .put("likes", JSONArray(syncedLikes.orEmpty().toList()))
                .put("lastSync", lastSync)
                .toString(),
        )
    }
}
