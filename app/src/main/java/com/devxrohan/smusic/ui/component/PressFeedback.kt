@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.utils.rememberPreference

/** Uses the same interaction source as the click target, including cancelled presses. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.pressFeedback(source: InteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) 0.96f else 1f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "controlPress",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun appButtonShapes(height: Dp? = null, shape: Shape? = null): ButtonShapes {
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    return when {
        reduceMotion -> ButtonDefaults.shapes(shape = shape ?: CircleShape, pressedShape = shape ?: CircleShape)
        shape != null -> ButtonDefaults.shapes(shape = shape)
        height != null -> ButtonDefaults.shapesFor(height)
        else -> ButtonDefaults.shapes()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun appToggleShapes(shapes: ToggleButtonShapes): ToggleButtonShapes {
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    val restingShape = MaterialTheme.shapes.medium
    return if (reduceMotion) {
        ToggleButtonDefaults.shapes(shape = restingShape, pressedShape = restingShape, checkedShape = restingShape)
    } else shapes
}
