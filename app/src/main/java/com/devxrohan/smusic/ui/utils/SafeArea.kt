package com.devxrohan.smusic.ui.utils

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets

/**
 * Bottom space a screen must keep clear for the global bottom area (docked/floating navigation bar,
 * mini player and the system navigation inset). Use it inside `contentPadding` / inside a scrolling
 * column so content scrolls under the bar and still rests clear of it at the end of the list.
 */
@Composable
fun bottomSafeArea(): Dp =
    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
