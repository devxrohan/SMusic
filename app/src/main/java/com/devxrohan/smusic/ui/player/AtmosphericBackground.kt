package com.devxrohan.smusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Static paper background; artwork tint does not alter its surface. */
@Composable
fun AtmosphericBackground(
    modifier: Modifier = Modifier,
    dynamicColor: Color? = null          // kept for API compatibility
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F7F4))  // off-white paper
    )
}
