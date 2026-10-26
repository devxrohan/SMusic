package com.devxrohan.smusic.viewmodels

import android.app.Application
import android.content.ContentUris
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devxrohan.smusic.models.LocalSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class GroupKind { ALBUM, ARTIST, FOLDER }

data class LocalGroup(
    val kind: GroupKind,
    val key: String,
    val title: String,
    val subtitle: String,
    val art: Uri?,
    val songs: List<LocalSong>,
)

/** Albums / artists / folders, built off the main thread so the screen only has to draw them. */
data class LocalGroups(
    val albums: List<LocalGroup> = emptyList(),
    val artists: List<LocalGroup> = emptyList(),
    val folders: List<LocalGroup> = emptyList(),
)

enum class LocalSortKey(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Date added"),
    DURATION("Duration"),
}

@HiltViewModel
class LocalSongsViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _songs = MutableStateFlow<List<LocalSong>>(emptyList())
    val songs: StateFlow<List<LocalSong>> = _songs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Search applied + sorted. Everything in the UI (tabs included) is built from this. */
    private val _filteredSongs = MutableStateFlow<List<LocalSong>>(emptyList())
    val filteredSongs: StateFlow<List<LocalSong>> = _filteredSongs.asStateFlow()

    private val _groups = MutableStateFlow(LocalGroups())
    val groups: StateFlow<LocalGroups> = _groups.asStateFlow()

    private var filterJob: Job? = null
    private var loadJob: Job? = null

    private val _sortKey = MutableStateFlow(LocalSortKey.TITLE)
    val sortKey: StateFlow<LocalSortKey> = _sortKey.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val reloadRunnable = Runnable { loadSongs() }

    // MediaStore fires a burst of change events while it scans; reload once the burst is over.
    private val contentObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            mainHandler.removeCallbacks(reloadRunnable)
            mainHandler.postDelayed(reloadRunnable, 1_500L)
        }
    }

    init {
        // Reopening the page is instant: show the last scan right away, refresh quietly after.
        cachedSongs?.let {
            _songs.value = it
            applyFilter()
        }
        application.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    fun loadSongs() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = _songs.value.isEmpty()
            val context = getApplication<Application>()
            val songList = mutableListOf<LocalSong>()

            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.RELATIVE_PATH
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Audio.Media.DATA
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.ALBUM_ID,
                pathColumn,
            )

            // Like Namida: every real audio file of 30 s or more, not only files flagged
            // IS_MUSIC (songs downloaded by other apps are often not flagged), but without
            // ringtones, notification sounds and alarms.
            val selection = "${MediaStore.Audio.Media.DURATION} >= 30000 AND " +
                "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
                "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
                "${MediaStore.Audio.Media.IS_ALARM} = 0"

            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                    val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val pathCol = cursor.getColumnIndexOrThrow(pathColumn)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val albumId = cursor.getLong(albumIdCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        )
                        val albumArtUri = ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), albumId
                        )
                        val rawPath = cursor.getString(pathCol).orEmpty()
                        val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            rawPath.trimEnd('/')
                        } else {
                            File(rawPath).parent.orEmpty()
                        }

                        val artist = cursor.getString(artistCol)
                            ?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown artist"
                        val album = cursor.getString(albumCol)
                            ?.takeUnless { it.isBlank() || it == "<unknown>" } ?: "Unknown album"

                        songList += LocalSong(
                            id = id,
                            title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                            artist = artist,
                            album = album,
                            duration = cursor.getLong(durationCol),
                            uri = contentUri,
                            albumArtUri = albumArtUri,
                            size = cursor.getLong(sizeCol),
                            dateAdded = cursor.getLong(dateCol),
                            folder = folder,
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            cachedSongs = songList
            if (songList != _songs.value) {
                _songs.value = songList
                applyFilter()
            }
            _isLoading.value = false
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        applyFilter()
    }

    /** Same key again flips the direction, a new key starts ascending (descending for "Date added"). */
    fun setSort(key: LocalSortKey) {
        if (_sortKey.value == key) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortKey.value = key
            _sortAscending.value = key != LocalSortKey.DATE_ADDED
        }
        applyFilter()
    }

    private fun applyFilter() {
        filterJob?.cancel()
        filterJob = viewModelScope.launch(Dispatchers.Default) {
            val query = _searchQuery.value
            val base = _songs.value
            val filtered = if (query.isBlank()) {
                base
            } else {
                base.filter { song ->
                    song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.album.contains(query, ignoreCase = true)
                }
            }
            val comparator: Comparator<LocalSong> = when (_sortKey.value) {
                LocalSortKey.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                LocalSortKey.ARTIST -> compareBy<LocalSong, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                LocalSortKey.ALBUM -> compareBy<LocalSong, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                LocalSortKey.DATE_ADDED -> compareBy { it.dateAdded }
                LocalSortKey.DURATION -> compareBy { it.duration }
            }
            val sorted = filtered.sortedWith(if (_sortAscending.value) comparator else comparator.reversed())

            val groups = LocalGroups(
                albums = sorted.groupBy { it.album }.map { (name, list) ->
                    LocalGroup(GroupKind.ALBUM, name, name, list.first().artist, list.first().albumArtUri, list)
                }.sortedBy { it.title.lowercase() },
                artists = sorted.groupBy { it.artist }.map { (name, list) ->
                    LocalGroup(GroupKind.ARTIST, name, name, "${list.size} songs", list.first().albumArtUri, list)
                }.sortedBy { it.title.lowercase() },
                folders = sorted.groupBy { it.folder }.map { (path, list) ->
                    LocalGroup(GroupKind.FOLDER, path, list.first().folderName, path.ifBlank { "Internal storage" }, list.first().albumArtUri, list)
                }.sortedBy { it.title.lowercase() },
            )
            _filteredSongs.value = sorted
            _groups.value = groups
        }
    }

    override fun onCleared() {
        super.onCleared()
        mainHandler.removeCallbacks(reloadRunnable)
        getApplication<Application>().contentResolver.unregisterContentObserver(contentObserver)
    }

    private companion object {
        /** Last scan, kept for the life of the process so reopening the page doesn't start from empty. */
        var cachedSongs: List<LocalSong>? = null
    }
}
