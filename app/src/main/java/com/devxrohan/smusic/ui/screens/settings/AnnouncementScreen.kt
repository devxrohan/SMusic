package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.utils.bottomSafeArea
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import com.devxrohan.smusic.utils.dataStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.devxrohan.smusic.R
import com.devxrohan.smusic.update.AnnouncementInfo
import com.devxrohan.smusic.update.AppNotificationManager

const val AnnouncementRoute = "announcement"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var announcements by remember { mutableStateOf<List<AnnouncementInfo>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<com.devxrohan.smusic.update.UpdateInfo?>(null) }

    // Reload whenever messages or the pending update change while this screen is open, so a message
    // that arrives right now shows up immediately (no need to leave and come back).
    LaunchedEffect(Unit) {
        context.dataStore.data
            .map {
                listOf(
                    it[com.devxrohan.smusic.constants.PushAnnouncementsKey],
                    it[com.devxrohan.smusic.constants.AnnouncementsCacheKey],
                    it[com.devxrohan.smusic.constants.PendingUpdateJsonKey],
                )
            }
            .distinctUntilChanged()
            .collect {
                announcements = AppNotificationManager.getCachedAnnouncements(context)
                // The card stays here until the update is actually installed.
                pendingUpdate = AppNotificationManager.getPendingUpdate(context)
                AppNotificationManager.markUpdateSeen(context) // hides the badge; the card stays until installed
                loaded = true
                AppNotificationManager.markAllAnnouncementsSeen(context)
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }
    ) { padding ->
        if (loaded && announcements.isEmpty() && pendingUpdate == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.notifications),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.no_announcements),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.no_announcements_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp + bottomSafeArea()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                pendingUpdate?.let { update ->
                    item(key = "pending_update") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = stringResource(R.string.update_notification_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.ui_smusic_v_is_ready_to_download, update.versionName),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        navController.navigate(com.devxrohan.smusic.ui.screens.settings.CheckForUpdateRoute)
                                    }
                                ) { Text(stringResource(R.string.ui_update_now)) }
                            }
                        }
                    }
                }
                items(announcements.reversed(), key = { it.id }) { announcement ->
                    // Compact by default (small thumbnail, 2 lines); tap to open the full text and the big picture.
                    var expanded by remember(announcement.id) { mutableStateOf(false) }
                    val hasImage = announcement.imageUrl.startsWith("https://", ignoreCase = true)
                    val canExpand = hasImage || announcement.message.length > 90 || announcement.message.contains('\n') ||
                        announcement.title.length > 32
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                announcements = announcements.filterNot { it.id == announcement.id }
                                coroutineScope.launch {
                                    AppNotificationManager.dismissAnnouncement(context, announcement.id)
                                }
                                true
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 22.dp),
                                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                                    Alignment.CenterStart else Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.ui_delete_notification),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(enabled = canExpand) { expanded = !expanded }
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .animateContentSize()
                            ) {
                                Row(verticalAlignment = Alignment.Top) {
                                    if (hasImage && !expanded) {
                                        AsyncImage(
                                            model = announcement.imageUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        if (announcement.title.isNotBlank()) {
                                            Text(
                                                text = announcement.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = if (expanded) Int.MAX_VALUE else 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                        }
                                        Text(
                                            text = announcement.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (canExpand) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            painter = painterResource(R.drawable.chevron_right),
                                            contentDescription = if (expanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(24.dp)
                                                .rotate(if (expanded) 90f else 0f)
                                        )
                                    }
                                }
                                if (hasImage && expanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AsyncImage(
                                        model = announcement.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(12.dp))
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
