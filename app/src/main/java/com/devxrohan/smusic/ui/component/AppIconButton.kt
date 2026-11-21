@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.utils.rememberPreference

/** Standard supplementary action with the app's Expressive press-shape behaviour. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    if (reduceMotion) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier, enabled = enabled,
            colors = colors, interactionSource = interactionSource, content = content)
    } else {
        androidx.compose.material3.IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
            shapes = IconButtonDefaults.shapes(),
            content = content,
        )
    }
}

/** Keeps existing long-press actions while confining added motion to opted-in screens. */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.pressFeedback(interactionSource, enabled),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}
