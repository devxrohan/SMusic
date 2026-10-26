package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Animated version (used when scroll offset exists)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleIconButton(
    icon: Int,
    onClick: () -> Unit,
    scrollOffset: Float = 0f,
    modifier: Modifier = Modifier,
    disableRipple: Boolean = false
) {
    Box(
        modifier = modifier
            .size(52.dp - (8 * scrollOffset).dp)
            .clip(CircleShape)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = 1f - (scrollOffset * 0.3f).coerceIn(0f, 0.3f)
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (disableRipple) null else androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .scale(1f - (scrollOffset * 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp - (4 * scrollOffset).dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Normal version (default use)
 */
@Composable
fun CircleIconButton(
    icon: Int,
    onClick: () -> Unit,
    disableRipple: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (disableRipple) null else androidx.compose.material3.ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
