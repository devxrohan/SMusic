package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

const val CheckForUpdateRoute = "check_for_update"

private enum class CheckStage { IDLE, CHECKING, UP_TO_DATE }

/** Formats a byte count as "XX.X MB", the way a phone's system-update screen does. */
private fun formatApkSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / (1024.0 * 1024.0)
    return "%.1f MB".format(mb)
}

@androidx.compose.runtime.Composable
fun CheckForUpdateScreen(
    navController: NavController,
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by updateViewModel.state.collectAsState()

    androidx.compose.runtime.DisposableEffect(Unit) {
        updateViewModel.updateManager.setSuppressGlobalDialog(true)
        onDispose { updateViewModel.updateManager.setSuppressGlobalDialog(false) }
    }

    LaunchedEffect(state) {
        val currentState = state
        if (currentState is UpdateState.ReadyToInstall && !currentState.apkFile.exists()) {
            updateViewModel.dismiss()
        }
    }

    var stage by remember { mutableStateOf(CheckStage.IDLE) }
    val currentVersionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: com.devxrohan.smusic.BuildConfig.VERSION_NAME
    }

    fun runCheck() {
        stage = CheckStage.CHECKING
        scope.launch {
            val result = updateViewModel.checkNow()
            stage = if (result is UpdateCheckResult.UpToDate) CheckStage.UP_TO_DATE else CheckStage.IDLE
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.check_for_update)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Only show the big status icon / check-button area when there's
            // no active update to act on — once one exists, that section
            // below takes over as the focal point, matching how a phone's
            // system-update screen replaces "checking" with the result.
            val info = when (val s = state) {
                is UpdateState.Available -> s.info
                is UpdateState.Downloading -> s.info
                is UpdateState.ReadyToInstall -> s.info
                is UpdateState.Failed -> s.info
                UpdateState.Idle -> null
            }

            if (info == null) {
                Icon(
                    painter = painterResource(R.mipmap.ic_launcher_monochrome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.current_version_label, currentVersionName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                AnimatedContent(
                    targetState = stage,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "checkStage"
                ) { currentStage ->
                    when (currentStage) {
                        CheckStage.CHECKING -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.checking_for_update),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                        CheckStage.UP_TO_DATE -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    painter = painterResource(R.drawable.done),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.up_to_date),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                                OutlinedButton(onClick = { runCheck() }) {
                                    Text(stringResource(R.string.check_for_update))
                                }
                            }
                        }
                        CheckStage.IDLE -> {
                            Button(onClick = { runCheck() }) {
                                Text(stringResource(R.string.check_for_update))
                            }
                        }
                    }
                }
            } else {
                UpdateResultSection(
                    state = state,
                    info = info,
                    onDownload = { updateViewModel.startDownload(info) },
                    onInstall = { file -> updateViewModel.install(file) },
                    onRetry = { runCheck() }
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@androidx.compose.runtime.Composable
private fun UpdateResultSection(
    state: UpdateState,
    info: UpdateInfo,
    onDownload: () -> Unit,
    onInstall: (java.io.File) -> Unit,
    onRetry: () -> Unit,
) {
    Icon(
        painter = painterResource(R.drawable.download),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(64.dp)
    )

    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = stringResource(R.string.update_version, info.versionName),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )

    val sizeText = formatApkSize(info.apkSize)
    if (sizeText.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (info.changelog.isNotBlank()) {
        Spacer(modifier = Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.whats_new),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = info.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(28.dp))

    when (state) {
        is UpdateState.Available -> {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.download))
            }
        }

        is UpdateState.Downloading -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${state.progress}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is UpdateState.ReadyToInstall -> {
            Button(
                onClick = { onInstall(state.apkFile) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.install))
            }
        }

        is UpdateState.Failed -> {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }

        UpdateState.Idle -> Unit
    }
}
