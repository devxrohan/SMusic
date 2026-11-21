package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import com.devxrohan.smusic.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.extensions.toMediaItem
import com.devxrohan.smusic.playback.queues.ListQueue
import com.devxrohan.smusic.utils.rememberPreference

internal val SpeedDialSongsKey = stringSetPreferencesKey("homeSpeedDialSongs")

private data class SpeedDialPreferences(val songs: Set<String>, val collections: Set<String>, val order: String)

@Composable
internal fun HomeSpeedDial(navController: NavController) {
    val context = LocalContext.current
    // A single preference snapshot prevents a frame mixing old membership with new order.
    val pins by remember(context) {
        context.dataStore.data.map { prefs ->
            SpeedDialPreferences(prefs[SpeedDialSongsKey].orEmpty(), prefs[SpeedDialCollectionsKey].orEmpty(), prefs[SpeedDialOrderKey] ?: "[]")
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = SpeedDialPreferences(
        context.dataStore[SpeedDialSongsKey].orEmpty(), context.dataStore[SpeedDialCollectionsKey].orEmpty(), context.dataStore[SpeedDialOrderKey] ?: "[]"))
    val ids = pins.songs
    val storedOrder = pins.order
    val collections = remember(pins.collections) { pins.collections.mapNotNull(SpeedDialCollection::decode).sortedBy { it.title } }
    val collectionsByRoute = remember(collections) { collections.associateBy { it.route } }
    val database = LocalDatabase.current
    val connection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var shuffling by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    if (ids.isEmpty() && collections.isEmpty()) return
    val entries = remember(ids, collections, storedOrder) {
        com.devxrohan.smusic.customization.SpeedDialOrder.reconcile(
            com.devxrohan.smusic.customization.SpeedDialOrder.decode(storedOrder),
            ids.sorted().map { "song:$it" } + collections.map { it.route })
    }
    val pages = 1 + ((entries.size - 8).coerceAtLeast(0) + 8) / 9
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { pages })
    val rows = ((minOf(entries.size + 1, 9) + 2) / 3).coerceIn(1, 3)
    val shuffleTile: @Composable (Modifier) -> Unit = { modifier ->
                    Surface(onClick = {
                        if (!shuffling) scope.launch {
                            shuffling = true
                            try {
                                val songs = withContext(Dispatchers.IO) {
                                    val direct = ids.mapNotNull { database.song(it).first() }
                                    val grouped = collections.flatMap { pin ->
                                        val id = pin.route.substringAfter('/')
                                        when {
                                            pin.route.startsWith("album/") -> database.albumSongs(id).first()
                                            pin.route.startsWith("artist/") -> database.artistSongsByCreateDateAsc(id).first()
                                            pin.route.startsWith("local_playlist/") -> database.playlistSongs(id).first().map { it.song }
                                            else -> emptyList()
                                        }
                                    }
                                    (direct + grouped).distinctBy { it.id }.shuffled()
                                }
                                if (songs.isNotEmpty()) connection?.playQueue(ListQueue("Speed dial", songs.map { it.toMediaItem() }))
                                else message = "Open a pinned collection to load its songs before shuffling."
                            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { message = "Could not load pinned songs. Try again." }
                            finally { shuffling = false }
                        }
                    }, enabled = !shuffling, shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
                        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(R.drawable.shuffle), null, Modifier.size(32.dp))
                            Text(if (shuffling) "Loading" else "Shuffle", style = MaterialTheme.typography.labelMedium)
                        }
                    }
    }
    Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Speed dial", color = homeContentColor(), style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp))
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
                val cell = (maxWidth - 20.dp) / 3
                androidx.compose.foundation.pager.HorizontalPager(state = pager,
                    verticalAlignment = Alignment.Top, pageSpacing = 12.dp,
                    modifier = Modifier.fillMaxWidth().height(cell * rows + 10.dp * (rows - 1))) { page ->
                    val slots: List<String?> = if (page == 0) listOf(null) + entries.take(8) else entries.drop(8 + (page - 1) * 9).take(9)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat((slots.size + 2) / 3) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                repeat(3) { column ->
                                    val index = row * 3 + column
                                    val tileModifier = Modifier.weight(1f).aspectRatio(1f)
                                    if (index >= slots.size) Spacer(tileModifier)
                                    else if (page == 0 && index == 0) shuffleTile(tileModifier)
                                    else {
                                        val key = slots[index]!!
                                        if (key.startsWith("song:")) {
                                            val id = key.removePrefix("song:")
                                            val song by remember(id, database) { database.song(id) }.collectAsState(initial = null)
                                            val track = song
                                            if (track == null) Spacer(tileModifier)
                                            else SpeedDialTile(track.title, track.thumbnailUrl, tileModifier) {
                                                connection?.playQueue(ListQueue("Speed dial", listOf(track.toMediaItem())))
                                            }
                                        } else {
                                            val pin = collectionsByRoute.getValue(key)
                                            SpeedDialTile(pin.title, pin.artwork, tileModifier) { navController.navigate(pin.route) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (pages > 1) Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages) { page ->
                    Box(Modifier.padding(horizontal = 4.dp).size(if (page == pager.currentPage) 8.dp else 6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (page == pager.currentPage) 1f else .3f)))
                }
            }
            }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = homeContentColor(),
            modifier = Modifier.padding(horizontal = 16.dp)) }
    }
}

@Composable
private fun SpeedDialTile(title: String, artwork: String?, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        MusicArtwork(artwork, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = .85f)))))
        Text(title, color = androidx.compose.ui.graphics.Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
        Icon(painterResource(R.drawable.speed_dial_pin), "Pinned",
            Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).rotate(14f),
            tint = androidx.compose.ui.graphics.Color.White)
    }
}
