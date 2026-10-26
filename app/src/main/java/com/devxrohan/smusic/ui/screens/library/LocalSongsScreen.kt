package com.devxrohan.smusic.ui.screens.library

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.models.LocalSong
import com.devxrohan.smusic.viewmodels.GroupKind
import com.devxrohan.smusic.viewmodels.LocalGroup
import com.devxrohan.smusic.viewmodels.LocalSongsViewModel
import com.devxrohan.smusic.viewmodels.LocalSortKey
import androidx.compose.ui.res.stringResource

private enum class LocalTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
}

/**
 * "Local music": every audio file on the phone (MediaStore), browsable by
 * songs / albums / artists / folders with search, sorting, play-all and shuffle.
 * Laid out after Namida's library: tabs on top, quick actions above the list,
 * tap a group to open its tracks.
 */
@Composable
fun LocalSongsScreen(
    navController: NavController,
    viewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val colors = MaterialTheme.colorScheme

    val songs by viewModel.filteredSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortKey by viewModel.sortKey.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()

    // ── Permission ───────────────────────────────────────────────────────────
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED)
    }
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        permissionDenied = !granted
        if (granted) viewModel.loadSongs()
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadSongs()
    }

    // ── What the player is doing right now (to highlight the active row) ──────
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isPlaying?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val activeId = mediaMetadata?.id

    // ── Tabs and drill-down ───────────────────────────────────────────────────
    var tab by remember { mutableStateOf(LocalTab.SONGS) }
    var openGroup by remember { mutableStateOf<Pair<GroupKind, String>?>(null) }

    val groups by viewModel.groups.collectAsState()
    val albums = groups.albums
    val artists = groups.artists
    val folders = groups.folders
    val notePainter = painterResource(R.drawable.music_note) // loaded once, not per row
    val currentGroup: LocalGroup? = openGroup?.let { (kind, key) ->
        when (kind) {
            GroupKind.ALBUM -> albums
            GroupKind.ARTIST -> artists
            GroupKind.FOLDER -> folders
        }.firstOrNull { it.key == key }
    }
    // Group disappeared (search changed / files removed): fall back to the tabs.
    LaunchedEffect(currentGroup, openGroup) {
        if (openGroup != null && currentGroup == null) openGroup = null
    }
    BackHandler(enabled = openGroup != null) { openGroup = null }

    // The list the user is looking at: playing it starts from the tapped row.
    val visibleSongs = currentGroup?.songs ?: songs

    fun playList(list: List<LocalSong>, startIndex: Int = 0, shuffle: Boolean = false) {
        val pc = playerConnection ?: return
        if (list.isEmpty()) return
        val ordered = if (shuffle) list.shuffled() else list
        pc.player.setMediaItems(ordered.map { it.toMediaItem() }, if (shuffle) 0 else startIndex, 0L)
        pc.player.prepare()
        pc.player.play()
    }

    fun playNext(song: LocalSong) {
        val pc = playerConnection ?: return
        if (pc.player.mediaItemCount == 0) playList(listOf(song)) else
            pc.player.addMediaItem(pc.player.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: LocalSong) {
        val pc = playerConnection ?: return
        if (pc.player.mediaItemCount == 0) playList(listOf(song)) else pc.player.addMediaItem(song.toMediaItem())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = { if (openGroup != null) openGroup = null else navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.back), tint = colors.onSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentGroup?.title ?: "Local music",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasPermission && visibleSongs.isNotEmpty()) {
                    val totalMs = remember(visibleSongs) { visibleSongs.sumOf { it.duration } }
                    Text(
                        text = stringResource(R.string.ui_songs, visibleSongs.size, formatTotalDuration(totalMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (hasPermission) {
                IconButton(onClick = { viewModel.loadSongs() }) {
                    Icon(painterResource(R.drawable.refresh), contentDescription = stringResource(R.string.ui_refresh), tint = colors.onSurface)
                }
            }
        }

        when {
            !hasPermission -> PermissionRequestCard(
                denied = permissionDenied,
                onRequest = {
                    if (permissionDenied) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier.fillMaxSize().padding(24.dp)
            )

            else -> {
                // Search
                if (songs.isNotEmpty() || searchQuery.isNotEmpty()) {
                    LocalSearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Tabs (hidden while looking inside an album / artist / folder)
                if (openGroup == null && (songs.isNotEmpty() || searchQuery.isNotEmpty())) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LocalTab.entries.forEach { entry ->
                            val selected = entry == tab
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (selected) colors.primary else colors.surfaceContainerHigh)
                                    .clickable { tab = entry }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = entry.label,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selected) colors.onPrimary else colors.onSurface,
                                )
                            }
                        }
                    }
                }

                val showSongList = openGroup != null || tab == LocalTab.SONGS

                when {
                    isLoading && songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(44.dp))
                    }

                    songs.isEmpty() && searchQuery.isNotEmpty() -> EmptyState(
                        icon = R.drawable.search,
                        title = stringResource(R.string.ui_no_results),
                        subtitle = stringResource(R.string.ui_nothing_matches, searchQuery),
                        modifier = Modifier.fillMaxSize()
                    )

                    songs.isEmpty() -> EmptyState(
                        icon = R.drawable.music_note,
                        title = stringResource(R.string.ui_no_local_songs_found),
                        subtitle = stringResource(R.string.ui_audio_files_of_at_least_30),
                        modifier = Modifier.fillMaxSize()
                    )

                    showSongList -> LazyColumn(
                        contentPadding = PaddingValues(
                            bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "actions") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Button(
                                    onClick = { playList(visibleSongs) },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(painterResource(R.drawable.play), null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.ui_play_all))
                                }
                                FilledTonalButton(
                                    onClick = { playList(visibleSongs, shuffle = true) },
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(painterResource(R.drawable.shuffle), null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.shuffle))
                                }
                                if (openGroup == null) {
                                    SortButton(sortKey, sortAscending, viewModel::setSort)
                                }
                            }
                        }
                        itemsIndexed(items = visibleSongs, key = { _, song -> song.id }, contentType = { _, _ -> "song" }) { index, song ->
                            LocalSongItem(
                                song = song,
                                isActive = activeId == song.uri.toString(),
                                isPlaying = isPlaying && activeId == song.uri.toString(),
                                placeholder = notePainter,
                                onClick = { playList(visibleSongs, startIndex = index) },
                                onPlayNext = { playNext(song) },
                                onAddToQueue = { addToQueue(song) },
                            )
                        }
                    }

                    tab == LocalTab.ALBUMS -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 8.dp,
                            bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(albums, key = { it.key }) { group ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { openGroup = group.kind to group.key }
                            ) {
                                ArtBox(
                                    uri = group.art,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    placeholder = notePainter,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(group.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${group.subtitle} \u2022 ${group.songs.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    else -> {
                        val groups = if (tab == LocalTab.ARTISTS) artists else folders
                        LazyColumn(
                            contentPadding = PaddingValues(
                                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 24.dp
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(groups, key = { it.key }) { group ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openGroup = group.kind to group.key }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    if (group.kind == GroupKind.FOLDER) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.primary.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(painterResource(R.drawable.folder), null, tint = colors.primary)
                                        }
                                    } else {
                                        ArtBox(group.art, Modifier.size(52.dp), CircleShape, notePainter)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(group.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = if (group.kind == GroupKind.FOLDER) "${group.songs.size} songs \u2022 ${group.subtitle}" else group.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Pieces ─────────────────────────────────────────────────────────────────────

@Composable
private fun ArtBox(
    uri: Uri?,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    placeholder: androidx.compose.ui.graphics.painter.Painter,
) {
    val context = LocalContext.current
    // Small decode size: tiles are at most ~170 dp, full-size album art made scrolling stutter.
    val request = remember(uri) {
        coil.request.ImageRequest.Builder(context)
            .data(uri)
            .size(256)
            .crossfade(false)
            .build()
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = placeholder,
            fallback = placeholder,
            placeholder = placeholder,
        )
    }
}

@Composable
private fun SortButton(sortKey: LocalSortKey, ascending: Boolean, onSort: (LocalSortKey) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.ui_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LocalSortKey.entries.forEach { key ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = key.label + if (key == sortKey) (if (ascending) "  \u2191" else "  \u2193") else "",
                            fontWeight = if (key == sortKey) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSort(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalSongItem(
    song: LocalSong,
    isActive: Boolean,
    isPlaying: Boolean,
    placeholder: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) colors.primary.copy(alpha = 0.10f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtBox(song.albumArtUri, Modifier.size(52.dp), RoundedCornerShape(10.dp), placeholder)
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isActive) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = song.durationText,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.ui_more), tint = colors.onSurfaceVariant)
            }
            if (menuOpen) {
                DropdownMenu(expanded = true, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.play_next)) }, onClick = { onPlayNext(); menuOpen = false })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_to_queue)) }, onClick = { onAddToQueue(); menuOpen = false })
                }
            }
        }
    }
}

@Composable
private fun LocalSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = colors.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurface),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ui_search_songs_artists_albums),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface.copy(alpha = 0.4f)
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier.weight(1f)
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.onSurface.copy(alpha = 0.2f))
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.clear), tint = colors.onSurface, modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun PermissionRequestCard(denied: Boolean, onRequest: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.size(80.dp).clip(CircleShape).background(colors.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.folder), null, tint = colors.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (denied) "Permission required" else "Access your music",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (denied) "Music access was denied. Enable it in the app settings to see songs stored on this phone."
            else "Allow access to the audio files on this phone to play your local music.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(50.dp)) {
            Icon(painterResource(R.drawable.folder), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (denied) "Open settings" else "Allow access")
        }
    }
}

@Composable
private fun EmptyState(icon: Int, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(24.dp)
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), textAlign = TextAlign.Center)
    }
}

private fun formatTotalDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

// ── LocalSong → MediaItem ──────────────────────────────────────────────────────
fun LocalSong.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .setTag(
            com.devxrohan.smusic.models.MediaMetadata(
                id = uri.toString(),
                title = title,
                artists = listOf(com.devxrohan.smusic.models.MediaMetadata.Artist(id = null, name = artist)),
                album = com.devxrohan.smusic.models.MediaMetadata.Album(id = "", title = album),
                duration = (duration / 1000).toInt(),
                thumbnailUrl = albumArtUri.toString(),
            )
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .build()
        )
        .build()
