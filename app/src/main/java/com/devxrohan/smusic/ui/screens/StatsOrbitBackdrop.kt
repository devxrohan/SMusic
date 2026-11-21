@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.isActive

/** The decorative diamond rotates only while this screen is resumed and motion is enabled. */
@Composable
internal fun StatsOrbitBackdrop(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val reduced by rememberPreference(DisableAnimationsKey, false)
    val owner = LocalLifecycleOwner.current
    val lifecycle by owner.lifecycle.currentStateFlow.collectAsState()
    val active = enabled && !reduced && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) while (isActive) {
            rotation.animateTo(rotation.value + 360f, tween(20_000, easing = LinearEasing))
            rotation.snapTo(rotation.value % 360f)
        }
    }
    Box(modifier) {
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 54.dp).size(220.dp)
            .graphicsLayer { rotationZ = rotation.value }
            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .09f),
                MaterialShapes.PuffyDiamond.toShape()))
    }
}
