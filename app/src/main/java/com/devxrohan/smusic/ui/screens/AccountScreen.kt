package com.devxrohan.smusic.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.datastore.preferences.core.edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.GridThumbnailHeight
import com.devxrohan.smusic.constants.InnerTubeCookieKey
import com.devxrohan.smusic.constants.AccountNameKey
import com.devxrohan.smusic.constants.AccountEmailKey
import com.devxrohan.smusic.constants.AccountChannelHandleKey
import com.devxrohan.smusic.constants.AccountPhotoUrlKey
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.ui.component.IconButton
import com.devxrohan.smusic.ui.component.LocalMenuState
import com.devxrohan.smusic.ui.component.YouTubeGridItem
import com.devxrohan.smusic.ui.component.shimmer.GridItemPlaceHolder
import com.devxrohan.smusic.ui.component.shimmer.ShimmerHost
import com.devxrohan.smusic.ui.menu.YouTubeAlbumMenu
import com.devxrohan.smusic.ui.menu.YouTubeArtistMenu
import com.devxrohan.smusic.ui.menu.YouTubePlaylistMenu
import com.devxrohan.smusic.ui.utils.backToMain
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.signOutYouTubeAccount
import com.devxrohan.smusic.utils.rememberPreference
import androidx.compose.ui.text.font.FontWeight
import com.devxrohan.smusic.viewmodels.AccountViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = androidx.datastore.preferences.core.emptyPreferences())
    val cookie = prefs[InnerTubeCookieKey].orEmpty()
    val isYouTubeLoggedIn = cookie.isNotBlank() && "SAPISID" in parseCookieString(cookie)
    val accountName = prefs[AccountNameKey].orEmpty()
    val accountEmail = prefs[AccountEmailKey].orEmpty()
    val accountHandle = prefs[AccountChannelHandleKey].orEmpty()
    val accountPhoto = prefs[AccountPhotoUrlKey].orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.account)) },
            navigationIcon = {
                IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            },
            scrollBehavior = scrollBehavior,
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Box(Modifier.size(92.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (isYouTubeLoggedIn && accountPhoto.startsWith("https://")) {
                    AsyncImage(model = accountPhoto, contentDescription = stringResource(R.string.ui_youtube_profile_photo), modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(painterResource(R.drawable.person), contentDescription = stringResource(R.string.ui_smusic_profile), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Text(
                text = if (!isYouTubeLoggedIn) "SMusic profile" else accountName.ifBlank { "YouTube account" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = if (!isYouTubeLoggedIn) "Your personal SMusic listening space" else accountEmail.ifBlank { accountHandle.ifBlank { "YouTube Music connected" } },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { navController.navigate("login") }, modifier = Modifier.weight(1f), enabled = !isYouTubeLoggedIn) {
                Text(stringResource(R.string.ui_add_account))
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        // Only the account session is removed; local library, history and Pulse stay.
                        runCatching { signOutYouTubeAccount(context) }
                    }
                }, modifier = Modifier.weight(1f), enabled = isYouTubeLoggedIn || accountName.isNotBlank() || accountEmail.isNotBlank()
            ) { Text(stringResource(R.string.account_sign_out)) }
        }
        if (isYouTubeLoggedIn) {
            YouTubeAccountContent(navController, viewModel, modifier = Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
                Text(stringResource(R.string.ui_add_a_youtube_account_to_bring))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeAccountContent(
    navController: NavController,
    viewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val playlists by viewModel.playlists.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()

    LazyVerticalGrid(
        modifier = modifier.fillMaxWidth(),
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        items(items = playlists.orEmpty(), key = { it.id }) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier.combinedClickable(
                    onClick = { navController.navigate("online_playlist/${item.id}") },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubePlaylistMenu(
                                playlist = item,
                                coroutineScope = coroutineScope,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                ),
            )
        }

        items(items = albums.orEmpty(), key = { it.id }) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier.combinedClickable(
                    onClick = { navController.navigate("album/${item.id}") },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubeAlbumMenu(
                                albumItem = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                ),
            )
        }

        items(items = artists.orEmpty(), key = { it.id }) { item ->
            YouTubeGridItem(
                item = item,
                fillMaxWidth = true,
                modifier = Modifier.combinedClickable(
                    onClick = { navController.navigate("artist/${item.id}") },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubeArtistMenu(
                                artist = item,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                ),
            )
        }

        if (playlists == null) {
            items(8) {
                ShimmerHost { GridItemPlaceHolder(fillMaxWidth = true) }
            }
        }
    }
}

