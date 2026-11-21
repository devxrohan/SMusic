package com.devxrohan.smusic.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.devxrohan.smusic.LocalPlayerConnection
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.constants.ProfileBeatMotionKey
import com.devxrohan.smusic.utils.rememberPreference

/** Visible-only rotation; pulse strength comes from captured audio, never a simulated beat. */
@Composable
internal fun ProfileBeatFrame(content: @Composable () -> Unit) {
    val connection = LocalPlayerConnection.current
    val enabled by rememberPreference(ProfileBeatMotionKey, true)
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    val owner = LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateFlow.collectAsState()
    if (connection == null || !enabled || reduceMotion || !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
        Box { content() }
        return
    }
    val playing by connection.isPlaying.collectAsState()
    LaunchedEffect(connection, playing) {
        if (playing) runCatching { connection.service.ensureVisualizer() }
    }
    val audio by connection.service.visualizerManager.stats.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.RESUMED)
    val strength = if (playing && audio.captured) audio.bass.coerceIn(0f, 1f) else 0f
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            rotation.animateTo(rotation.value + 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)))
        }
    }
    val pulse = animateFloatAsState(strength, tween(100), label = "profileBeat")
    Box(Modifier.graphicsLayer {
        val beat = pulse.value
        scaleX = 1f + beat * .12f
        scaleY = 1f + beat * .12f
        rotationZ = rotation.value + beat * 8f
    }) { content() }
}
