package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.devxrohan.smusic.LocalDatabase
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.DisableScreenshotKey
import com.devxrohan.smusic.constants.PauseListenHistoryKey
import com.devxrohan.smusic.constants.PauseSearchHistoryKey
import com.devxrohan.smusic.ui.component.IconButton
import com.devxrohan.smusic.ui.component.PreferenceEntry
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import com.devxrohan.smusic.ui.component.SettingsPage
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.ui.utils.backToMain
import com.devxrohan.smusic.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(
        key = PauseListenHistoryKey,
        defaultValue = false
    )
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(
        key = PauseSearchHistoryKey,
        defaultValue = false
    )
    val (useRotatingProxy, onUseRotatingProxyChange) = rememberPreference(
        key = com.devxrohan.smusic.constants.UseRotatingProxyKey,
        defaultValue = false
    )
    val context = LocalContext.current
    val (enableUpdateNotifications, onEnableUpdateNotificationsChange) = rememberPreference(
        key = com.devxrohan.smusic.constants.EnableUpdateNotificationKey,
        defaultValue = true
    )

    var showClearListenHistoryDialog by remember { mutableStateOf(false) }
    var showClearSearchHistoryDialog by remember { mutableStateOf(false) }

    SettingsPage(
        title = stringResource(R.string.privacy),
        navController = navController,
        scrollBehavior = scrollBehavior,
    ) {
        SettingsGeneralCategory(
            title = stringResource(R.string.listen_history),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.pause_listen_history)) },
                    icon = { Icon(painterResource(R.drawable.history), null) },
                    checked = pauseListenHistory,
                    onCheckedChange = onPauseListenHistoryChange
                )},

                {PreferenceEntry(
                    title = { Text(stringResource(R.string.clear_listen_history)) },
                    icon = { Icon(painterResource(R.drawable.delete_history), null) },
                    onClick = { showClearListenHistoryDialog = true }
                )},
            )
        )
        SettingsGeneralCategory(
            title = stringResource(R.string.search_history),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.pause_search_history)) },
                    icon = { Icon(painterResource(R.drawable.search_off), null) },
                    checked = pauseSearchHistory,
                    onCheckedChange = onPauseSearchHistoryChange
                )},

                {PreferenceEntry(
                    title = { Text(stringResource(R.string.clear_search_history)) },
                    icon = { Icon(painterResource(R.drawable.clear_all), null) },
                    onClick = { showClearSearchHistoryDialog = true }
                )},
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.notifications),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.enable_update_notifications)) },
                    description = stringResource(R.string.enable_update_notifications_desc),
                    icon = { Icon(painterResource(R.drawable.notifications), null) },
                    checked = enableUpdateNotifications,
                    onCheckedChange = { enabled ->
                        onEnableUpdateNotificationsChange(enabled)
                        if (enabled) {
                            com.devxrohan.smusic.update.AppNotificationManager.schedulePeriodicCheck(context)
                        } else {
                            com.devxrohan.smusic.update.AppNotificationManager.cancelPeriodicCheck(context)
                        }
                    }
                )},
            )
        )

        SettingsGeneralCategory(
            title = stringResource(R.string.advanced_network),
            items = listOf(
                {SwitchPreference(
                    title = { Text(stringResource(R.string.use_rotating_proxy)) },
                    description = stringResource(R.string.use_rotating_proxy_desc),
                    icon = { Icon(painterResource(R.drawable.security), null) },
                    checked = useRotatingProxy,
                    onCheckedChange = onUseRotatingProxyChange
                )},
            )
        )
    }


    if (showClearListenHistoryDialog) {
        ConfirmationDialog2(
            title = stringResource(R.string.clear_listen_history),
            message = stringResource(R.string.clear_listen_history_confirm),
            onDismiss = { showClearListenHistoryDialog = false },
            onConfirm = {
                showClearListenHistoryDialog = false
                database.query { clearListenHistory() }
            }
        )
    }

    if (showClearSearchHistoryDialog) {
        ConfirmationDialog2(
            title = stringResource(R.string.clear_search_history),
            message = stringResource(R.string.clear_search_history_confirm),
            onDismiss = { showClearSearchHistoryDialog = false },
            onConfirm = {
                showClearSearchHistoryDialog = false
                database.query { clearSearchHistory() }
            }
        )
    }
}

@Composable
private fun ConfirmationDialog2(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(android.R.string.ok),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(android.R.string.cancel),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
