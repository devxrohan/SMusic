package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.utils.bottomSafeArea
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import com.devxrohan.smusic.update.UpdateCheckResult
import com.devxrohan.smusic.update.UpdateInfo
import com.devxrohan.smusic.update.UpdateState
import com.devxrohan.smusic.update.UpdateViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.devxrohan.smusic.constants.EnableUpdateNotificationKey
import com.devxrohan.smusic.update.AppNotificationManager
import com.devxrohan.smusic.utils.rememberPreference

const val CheckForUpdateRoute = "check_for_update"

private enum class CheckStage { IDLE, CHECKING, UP_TO_DATE, FAILED }

private fun formatApkSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckForUpdateScreen(
    navController: NavController,
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by updateViewModel.state.collectAsState()
    var stage by remember { mutableStateOf(CheckStage.IDLE) }
    var checkError by remember { mutableStateOf("") }
    var releaseHistory by remember { mutableStateOf<List<com.devxrohan.smusic.update.GitHubRelease>>(emptyList()) }
    val currentVersionName = com.devxrohan.smusic.BuildConfig.VERSION_NAME

    LaunchedEffect(Unit) {
        // Opened from the bell / notification: show the pending update right away (no network).
        updateViewModel.restorePending()
        releaseHistory = updateViewModel.releaseHistory().take(8)
    }

    DisposableEffect(Unit) {
        updateViewModel.updateManager.setSuppressGlobalDialog(true)
        onDispose { updateViewModel.updateManager.setSuppressGlobalDialog(false) }
    }

    fun runCheck() {
        stage = CheckStage.CHECKING
        checkError = ""
        scope.launch {
            val result = updateViewModel.checkNow()
            when (result) {
                is UpdateCheckResult.UpToDate -> stage = CheckStage.UP_TO_DATE
                is UpdateCheckResult.Available -> stage = CheckStage.IDLE
                is UpdateCheckResult.Failed -> {
                    checkError = result.message
                    stage = CheckStage.FAILED
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.check_for_update), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val info = when (val s = state) {
            is UpdateState.Available -> s.info
            is UpdateState.Downloading -> s.info
            is UpdateState.ReadyToInstall -> s.info
            is UpdateState.Failed -> s.info
            UpdateState.Idle -> null
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomSafeArea()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))

            if (info == null) {
                UpdateHero(
                    currentVersionName = currentVersionName,
                    stage = stage,
                    onCheck = ::runCheck,
                    errorMessage = checkError,
                )
            } else {
                UpdateResultSection(
                    state = state,
                    info = info,
                    onDownload = { updateViewModel.startDownload(info) },
                    onInstall = { updateViewModel.install(it) },
                    onBrowser = { updateViewModel.openReleaseInBrowser(info) },
                    onRetry = ::runCheck,
                )
            }

            Spacer(Modifier.height(18.dp))
            UpdateNotificationsToggle()

            if (releaseHistory.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                ReleaseHistorySection(releases = releaseHistory)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun UpdateHero(
    currentVersionName: String,
    stage: CheckStage,
    onCheck: () -> Unit,
    errorMessage: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.mipmap.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.ui_smusic_2), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                stringResource(R.string.current_version_label, currentVersionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(22.dp))
            AnimatedContent(
                targetState = stage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "updateStatus",
            ) { currentStage ->
                when (currentStage) {
                    CheckStage.CHECKING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.checking_for_update))
                        }
                    }
                    CheckStage.UP_TO_DATE -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(R.drawable.done),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp),
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(stringResource(R.string.up_to_date), fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(14.dp))
                            OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.check_for_update)) }
                        }
                    }
                    CheckStage.FAILED -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.ui_couldnt_reach_the_update_servers),
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.ui_the_latest_download_was_opened_in),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    CheckStage.IDLE -> Button(onClick = onCheck) { Text(stringResource(R.string.check_for_update)) }
                }
            }
        }
    }
}

@Composable
private fun ReleaseHistorySection(
    releases: List<com.devxrohan.smusic.update.GitHubRelease>,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.ui_release_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        releases.forEachIndexed { index, release ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        release.versionName.ifBlank { release.tag_name },
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (release.published_at.isNullOrBlank()) "" else release.published_at!!.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!release.body.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        com.devxrohan.smusic.update.formatReleaseNotes(release.body!!),
                        maxLines = 3,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index != releases.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun UpdateResultSection(
    state: UpdateState,
    info: UpdateInfo,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onBrowser: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.download),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.new_version_available), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "v${info.versionName}${formatApkSize(info.apkSize).let { if (it.isBlank()) "" else " • $it" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (info.changelog.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.whats_new), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(com.devxrohan.smusic.update.formatReleaseNotes(info.changelog), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))
        when (state) {
            is UpdateState.Available -> {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(if (info.apkUrl.isBlank()) "Open release" else stringResource(R.string.download))
                }
                if (info.releaseUrl.isNotBlank()) {
                    TextButton(onClick = onBrowser) { Text(stringResource(R.string.ui_download_in_browser)) }
                }
            }
            is UpdateState.Downloading -> {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.ui_downloading, state.progress), fontWeight = FontWeight.Medium)
            }
            is UpdateState.ReadyToInstall -> {
                Button(onClick = { onInstall(state.apkFile) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.install))
                }
            }
            is UpdateState.Failed -> {
                Text(state.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBrowser, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_open_download_in_browser))
                }
            }
            UpdateState.Idle -> Unit
        }
    }
}

@Composable
private fun UpdateNotificationsToggle() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val (enabled, _) = rememberPreference(EnableUpdateNotificationKey, defaultValue = true)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.enable_update_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Notify me when a new version of SMusic is available. " +
                        "If turned off, updates still appear in the app's Notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    scope.launch { AppNotificationManager.setUpdateNotificationsEnabled(context, on) }
                },
            )
        }
    }
}
