package com.devxrohan.smusic.ui.utils

import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.devxrohan.smusic.LocalPlayerAwareWindowInsets

/** Reserve bottom space for navigation, mini player and system insets. */
@Composable
fun bottomSafeArea(): Dp =
    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
