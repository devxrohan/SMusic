@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.devxrohan.smusic.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.playback.queues.ListQueue
import com.devxrohan.smusic.utils.rememberPreference
import com.devxrohan.smusic.viewmodels.LibrarySyncViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LibrarySyncCard(onAccountClick: () -> Unit, active: Boolean, viewModel: LibrarySyncViewModel = hiltViewModel()) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    if (!connected) return
    val state by viewModel.state.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val view = LocalView.current
    var visible by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(visible, lifecycle, reduceMotion, active) {
        if (active && visible && lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !reduceMotion) {
            while (true) rotation.animateTo(rotation.value + 360f, tween(24_000, easing = LinearEasing))
        }
    }
    val scheme = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().onGloballyPositioned {
            val bounds = it.boundsInWindow()
            visible = bounds.bottom > 0 && bounds.top < view.height && bounds.right > 0 && bounds.left < view.width
        }.then(if (reduceMotion) Modifier else Modifier.animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()))) {
        Box {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 45.dp, y = (-48).dp).size(190.dp)
                .graphicsLayer { rotationZ = rotation.value; alpha = 0.07f }
                .background(scheme.primary, MaterialShapes.Cookie9Sided.toShape()))
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp)) {
                        artwork.take(3).forEachIndexed { index, url ->
                            AsyncImage(url, null, Modifier.offset(x = (index * 9).dp, y = (index * 3).dp).size(44.dp)
                                .graphicsLayer { rotationZ = (index - 1) * 8f }.clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        }
                        Surface(shape = MaterialShapes.Cookie6Sided.toShape(), color = scheme.secondaryContainer,
                            modifier = Modifier.align(if (artwork.isEmpty()) Alignment.Center else Alignment.BottomEnd)) {
                            Icon(painterResource(R.drawable.sync), null, Modifier.padding(10.dp).size(22.dp), tint = scheme.onSecondaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Your music, in sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Spotify · YouTube Music", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                }
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                if (state.syncing) LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::syncNow, enabled = !state.syncing, shapes = appButtonShapes(shape = RoundedCornerShape(18.dp)),
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
                        Text(if (state.syncing) "Syncing…" else "Sync now", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                    FilledTonalButton(onClick = {
                        preparing = true
                        scope.launch {
                            try {
                                val items = viewModel.somethingToPlay()
                                if (items.isEmpty()) viewModel.noMusic() else player?.playQueue(ListQueue("Your library", items))
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (_: Exception) { viewModel.noMusic()
                            } finally { preparing = false }
                        }
                    }, enabled = !preparing && player != null, shapes = appButtonShapes(shape = RoundedCornerShape(18.dp)),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
                        Text(if (preparing) "Getting music…" else "Play mix", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
                if (state.connectAccounts) TextButton(onClick = onAccountClick) { Text("Connect accounts") }
            }
        }
    }
}
