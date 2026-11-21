package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Both destinations share one cell size, including the unpaired last card. */
@Composable
internal fun <T> CategoryCardGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(T) -> Unit,
) {
    val density = LocalDensity.current
    val typography = MaterialTheme.typography
    val cardHeight = with(density) {
        // Reserve identical text slots instead of sizing each row from its longest label.
        84.dp + typography.titleMedium.lineHeight.toDp() * 2 +
            typography.bodySmall.lineHeight.toDp() * 2
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 300.dp || density.fontScale >= 1.5f) 1 else 2
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(cardHeight),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item -> content(item) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
