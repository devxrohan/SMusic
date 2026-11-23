@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.component.appButtonShapes

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.devxrohan.smusic.utils.AutoBackupManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.annotation.ExperimentalCoilApi
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.db.entities.Song
import com.devxrohan.smusic.extensions.tryOrNull
import com.devxrohan.smusic.ui.component.AppIconButton as IconButton
import com.devxrohan.smusic.ui.component.PreferenceEntry
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.ui.menu.OnlinePlaylistAdder
import com.devxrohan.smusic.ui.utils.formatFileSize
import com.devxrohan.smusic.viewmodels.BackupRestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BackupAndRestore(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val coroutineScope = rememberCoroutineScope()
    val playerCache = LocalPlayerConnection.current?.service?.playerCache

    // Statuses
    var showVisitorDataDialog by remember { mutableStateOf(false) }
    var showVisitorDataResetDialog by remember { mutableStateOf(false) }
    var importedTitle by remember { mutableStateOf("") }
    val importedSongs = remember { mutableStateListOf<Song>() }
    var showChoosePlaylistDialogOnline by remember { mutableStateOf(false) }
    var isProgressStarted by remember { mutableStateOf(false) }
    var progressPercentage by remember { mutableIntStateOf(0) }


    // Device / Android OS backup state
    LaunchedEffect(Unit) { viewModel.loadOsBackupState(context) }
    val lastOsBackupTime by viewModel.lastOsBackupTime.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val isRestoring by viewModel.isRestoring.collectAsState()
    val backupSizeString by viewModel.backupSizeString.collectAsState()
    val isAutoBackupToStorage by viewModel.isAutoBackupToStorage.collectAsState()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showStorageRestoreConfirmDialog by remember { mutableStateOf(false) }

    val justNowText = stringResource(R.string.backup_time_just_now)
    val notYetText = stringResource(R.string.backup_not_yet)
    val lastBackupPrefix = stringResource(R.string.last_backup_prefix)
    val todayAtFormat = stringResource(R.string.backup_time_today)
    val yesterdayAtFormat = stringResource(R.string.backup_time_yesterday)
    val formattedLastBackup = remember(lastOsBackupTime) {
        if (lastOsBackupTime <= 0L) {
            null
        } else {
            val diff = System.currentTimeMillis() - lastOsBackupTime
            val ldt = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(lastOsBackupTime), java.time.ZoneId.systemDefault()
            )
            val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
            when {
                diff < 60_000L -> justNowText
                java.time.LocalDate.now() == ldt.toLocalDate() -> todayAtFormat.format(ldt.format(timeFormatter))
                java.time.LocalDate.now().minusDays(1) == ldt.toLocalDate() -> yesterdayAtFormat.format(ldt.format(timeFormatter))
                else -> ldt.format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
            }
        }
    }

    // Cache stats
    var playerCacheSize by remember { mutableLongStateOf(tryOrNull { playerCache?.cacheSpace } ?: 0L) }
    var isClearing by remember { mutableStateOf(false) }

    val animatedPlayerCacheSize by animateFloatAsState(
        targetValue = if (playerCacheSize > 0) 1f else 0f,
        label = "playerCacheProgress"
    )

    // Update cache size
    LaunchedEffect(playerCache) {
        while (true) {
            delay(1000)
            playerCacheSize = tryOrNull { playerCache?.cacheSpace } ?: 0L
        }
    }

    // Launchers
    val backupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                viewModel.backup(context, uri)
            }
        }

    val restoreLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                viewModel.restore(context, uri)
            }
        }

    val backupCacheLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) viewModel.backupCache(context, uri)
        }

    val restoreCacheLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.restoreCache(context, uri)
        }

    val storagePermissionGrantedText = stringResource(R.string.backup_storage_permission_granted)
    val storagePermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.any { it }) {
                Toast.makeText(context, storagePermissionGrantedText, Toast.LENGTH_SHORT).show()
            }
        }

    // Opens the "all files access" screen (Android 11+) or asks for the legacy storage permission.
    fun requestStorageAccess() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            } catch (_: Exception) {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    val importPlaylistFromCsv =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val result = viewModel.importPlaylistFromCsv(context, uri)
            importedSongs.clear()
            importedSongs.addAll(result)
            if (importedSongs.isNotEmpty()) {
                showChoosePlaylistDialogOnline = true
            }
        }

    val importM3uLauncherOnline =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val result = viewModel.loadM3UOnline(context, uri)
            importedSongs.clear()
            importedSongs.addAll(result)
            if (importedSongs.isNotEmpty()) {
                showChoosePlaylistDialogOnline = true
            }
        }

    var restoreStatus by remember { mutableStateOf(com.devxrohan.smusic.utils.AppBackup.restoreStatus(context)) }
    SettingsPage(
        title = stringResource(R.string.backup_restore),
        navController = navController,
        scrollBehavior = scrollBehavior,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        restoreStatus?.let { message ->
            SettingsGeneralCategory(title = "Restore status", items = listOf({
                PreferenceEntry(title = { Text(message) }, trailingContent = {
                    TextButton(shapes = appButtonShapes(), onClick = { com.devxrohan.smusic.utils.AppBackup.dismissRestoreStatus(context); restoreStatus = null }) { Text("Dismiss") }
                })
            }))
        }
        // Android OS backup. Android restores the real app data automatically during reinstall, before MainActivity.
        SettingsGeneralCategory(
            title = stringResource(R.string.android_os_backup_category),
            items = listOf(
                {PreferenceEntry(
                    title = { Text(stringResource(R.string.backup_now_everything)) },
                    icon = { Icon(painterResource(R.drawable.backup), null) },
                    description = when {
                        isBackingUp -> stringResource(R.string.backup_status_syncing)
                        formattedLastBackup != null -> "$lastBackupPrefix$formattedLastBackup · $backupSizeString"
                        else -> notYetText
                    },
                    isEnabled = !isBackingUp && !isRestoring,
                    onClick = { viewModel.backupNow(context) }
                )}
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.backup_storage_category),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.backup_storage_auto_title)) },
                    description = stringResource(R.string.backup_storage_auto_desc),
                    icon = { Icon(painterResource(R.drawable.save_to_storage), null) },
                    checked = isAutoBackupToStorage,
                    onCheckedChange = { enabled ->
                        if (enabled && !AutoBackupManager.hasStoragePermission(context)) requestStorageAccess()
                        viewModel.setAutoBackupToStorage(context, enabled)
                    }
                )},
                {PreferenceEntry(
                    title = { Text(stringResource(R.string.backup_storage_now_title)) },
                    icon = { Icon(painterResource(R.drawable.backup), null) },
                    description = stringResource(R.string.backup_storage_now_desc),
                    onClick = {
                        if (!AutoBackupManager.hasStoragePermission(context)) {
                            requestStorageAccess()
                        } else {
                            viewModel.backupToStorageNow(context) { success ->
                            Toast.makeText(
                                context,
                                if (success) R.string.backup_storage_saved else R.string.backup_storage_failed,
                                Toast.LENGTH_SHORT
                            ).show()
                            }
                        }
                    }
                )},
                {PreferenceEntry(
                    title = { Text(stringResource(R.string.backup_storage_restore_title)) },
                    icon = { Icon(painterResource(R.drawable.restore), null) },
                    description = stringResource(R.string.backup_storage_restore_desc),
                    onClick = {
                        if (!AutoBackupManager.hasStoragePermission(context)) {
                            requestStorageAccess()
                        } else {
                            val file = AutoBackupManager.findStorageBackupFile()
                            if (file != null && file.exists() && file.length() > 0L) {
                                showStorageRestoreConfirmDialog = true
                            } else {
                                Toast.makeText(context, R.string.backup_storage_not_found, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )}
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.backup_restore),
            items = listOf(
                {PreferenceEntry(
                    title = { Text(stringResource(R.string.backup)) },
                    icon = { Icon(painterResource(R.drawable.backup), null) },
                    description = stringResource(R.string.backup_description),
                    onClick = {
                        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        backupLauncher.launch(
                            "${resources.getString(R.string.app_name)}_${
                                LocalDateTime.now().format(formatter)
                            }.backup"
                        )
                    }
                )},
                {PreferenceEntry(
                    title = { Text(stringResource(R.string.restore)) },
                    icon = { Icon(painterResource(R.drawable.restore), null) },
                    description = stringResource(R.string.restore_description),
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/octet-stream"))
                    }
                )},
                {PreferenceEntry(
                    title = { Text(stringResource(R.string.backup_cached_songs)) },
                    icon = { Icon(painterResource(R.drawable.cached), null) },
                    description = stringResource(R.string.backup_cached_songs_desc),
                    onClick = {
                        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                        backupCacheLauncher.launch("SMusic_Cache_${LocalDateTime.now().format(formatter)}.smusiccache")
                    }
                )},
                {PreferenceEntry(
                    title = { Text(stringResource(R.string.restore_cached_songs)) },
                    icon = { Icon(painterResource(R.drawable.restore), null) },
                    description = stringResource(R.string.restore_cached_songs_desc),
                    onClick = {
                        restoreCacheLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                )},

            )
        )

        // VISITOR_DATA Card
        MinimalVisitorDataCard(
            playerCacheSize = playerCacheSize,
            progress = animatedPlayerCacheSize,
            isClearing = isClearing,
            onResetClick = { showVisitorDataResetDialog = true },
            onInfoClick = { showVisitorDataDialog = true }
        )
    }

    // Dialogs
    if (showRestoreConfirmDialog) {
        MinimalConfirmDialog(
            icon = painterResource(R.drawable.restore),
            title = stringResource(R.string.backup_restore_confirm_title),
            message = stringResource(R.string.backup_restore_confirm_desc),
            confirmText = stringResource(R.string.backup_restore_action),
            onConfirm = {
                showRestoreConfirmDialog = false
                viewModel.restoreFromLatestBackup(context)
            },
            onDismiss = { showRestoreConfirmDialog = false }
        )
    }

    if (showStorageRestoreConfirmDialog) {
        MinimalConfirmDialog(
            icon = painterResource(R.drawable.restore),
            title = stringResource(R.string.backup_storage_restore_confirm_title),
            message = stringResource(R.string.backup_storage_restore_confirm_desc),
            confirmText = stringResource(R.string.backup_restore_action),
            onConfirm = {
                showStorageRestoreConfirmDialog = false
                viewModel.restoreFromStorageNow(context) { success ->
                    if (!success) Toast.makeText(context, R.string.restore_failed, Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showStorageRestoreConfirmDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        MinimalConfirmDialog(
            icon = painterResource(R.drawable.delete),
            title = stringResource(R.string.backup_delete_confirm_title),
            message = stringResource(R.string.backup_delete_confirm_desc),
            confirmText = stringResource(R.string.backup_delete_action),
            onConfirm = {
                showDeleteConfirmDialog = false
                viewModel.deleteBackup(context)
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    if (showVisitorDataDialog) {
        MinimalInfoDialog(
            icon = painterResource(R.drawable.info),
            title = stringResource(R.string.visitor_data_info_title),
            message = stringResource(R.string.visitor_data_info_intro) + "\n\n" +
                    stringResource(R.string.visitor_data_info_problems) + "\n\n" +
                    stringResource(R.string.visitor_data_info_solution),
            onDismiss = { showVisitorDataDialog = false }
        )
    }

    if (showVisitorDataResetDialog) {
        MinimalConfirmDialog(
            icon = painterResource(R.drawable.replay),
            title = stringResource(R.string.visitor_data_reset_title),
            message = stringResource(R.string.visitor_data_reset_message),
            confirmText = stringResource(R.string.visitor_data_reset_confirm),
            onConfirm = {
                isClearing = true
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // Clear song cache
                        playerCache?.keys?.toList()?.forEach { key ->
                            tryOrNull { playerCache.removeResource(key) }
                        }

                        // Reset VISITOR_DATA
                        viewModel.resetVisitorData(context)

                        delay(500) // Short delay to ensure completion

                        withContext(Dispatchers.Main) {
                            playerCacheSize = 0L
                            isClearing = false
                            showVisitorDataResetDialog = false
                        }
                    } catch (e: Exception) {
                        Log.e("BackupRestore", "Error when resetting VISITOR_DATA", e)
                        withContext(Dispatchers.Main) {
                            isClearing = false
                            showVisitorDataResetDialog = false
                        }
                    }
                }
            },
            onDismiss = { showVisitorDataResetDialog = false }
        )
    }

    OnlinePlaylistAdder(
        isVisible = showChoosePlaylistDialogOnline,
        allowSyncing = false,
        initialTextFieldValue = importedTitle,
        songs = importedSongs,
        onDismiss = { showChoosePlaylistDialogOnline = false },
        onProgressStart = { newVal -> isProgressStarted = newVal },
        onPercentageChange = { newPercentage -> progressPercentage = newPercentage }
    )

    LaunchedEffect(progressPercentage, isProgressStarted) {
        if (isProgressStarted && progressPercentage == 99) {
            delay(10000)
            if (progressPercentage == 99) {
                isProgressStarted = false
                progressPercentage = 0
            }
        }
    }

    if (isProgressStarted) {
        MinimalLoadingOverlay(progress = progressPercentage)
    }
}


@Composable
private fun MinimalVisitorDataCard(
    playerCacheSize: Long,
    progress: Float,
    isClearing: Boolean,
    onResetClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.replay),
                        contentDescription = null,
//                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.visitor_data_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Text(
                text = stringResource(R.string.visitor_data_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Cache indicator
            if (playerCacheSize > 0 || isClearing) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (isClearing) 0f else progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isClearing) {
                                stringResource(R.string.cache_clearing)
                            } else {
                                stringResource(R.string.song_cache)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatFileSize(playerCacheSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onInfoClick,
                    modifier = Modifier.weight(1f),
                    shapes = appButtonShapes(shape = RoundedCornerShape(12.dp)),
                    enabled = !isClearing
                ) {
                    Icon(
                        painter = painterResource(R.drawable.help),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.info_button), style = MaterialTheme.typography.labelLarge)
                }

                FilledTonalButton(
                    onClick = onResetClick,
                    modifier = Modifier.weight(1f),
                    shapes = appButtonShapes(shape = RoundedCornerShape(12.dp)),
                    enabled = !isClearing
                ) {
                    if (isClearing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.replay),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.reset_button), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun MinimalLoadingOverlay(progress: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.size(64.dp),
                strokeWidth = 4.dp
            )
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.processing_songs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MinimalInfoDialog(
    icon: Painter,
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(shapes = appButtonShapes(), onClick = onDismiss) {
                Text(stringResource(R.string.understood))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun MinimalConfirmDialog(
    icon: Painter,
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shapes = appButtonShapes(shape = RoundedCornerShape(12.dp))) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(shapes = appButtonShapes(), onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
