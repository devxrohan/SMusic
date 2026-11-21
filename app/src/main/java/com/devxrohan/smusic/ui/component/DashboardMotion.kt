@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.utils.rememberPreference

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberDashboardProgress(target: Float, animateEntrance: Boolean = false): Float {
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    var entered by remember { mutableStateOf(!animateEntrance) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) target else 0f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "dashboardProgress",
    )
    return progress
}

/** A single entrance per header; no endless decorative animation or fabricated activity. */
@Composable
internal fun DashboardEntrance(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress = rememberDashboardProgress(if (entered) 1f else 0f)
    Box(modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * 12.dp.toPx() }) { content() }
}

@Composable
internal fun DashboardNumber(value: Long, format: (Long) -> String, style: TextStyle, modifier: Modifier = Modifier) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val fraction = rememberDashboardProgress(if (entered) 1f else 0f)
    Text(format((value.coerceAtLeast(0) * fraction).toLong()), modifier = modifier.clearAndSetSemantics { contentDescription = format(value) }, style = style)
}
