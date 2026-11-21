@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun LikeSourceSelector(source: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, availableSources: Set<String> = setOf("all", "smusic")) {
    val labels = listOf("all" to "All", "smusic" to "SMusic", "spotify" to "Spotify", "youtube" to "YouTube").filter { it.first in availableSources }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = MaterialTheme.typography.labelSmall
    val labelWidth = labels.maxOf { (_, label) -> measurer.measure(label, textStyle).size.width }
    val buttonWidth = with(density) { labelWidth.toDp() } + 16.dp
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEach { (key, label) ->
            val chosen = source == key
            ToggleButton(checked = chosen, onCheckedChange = { onSelect(key) },
                modifier = Modifier.width(buttonWidth.coerceAtLeast(52.dp)).height(40.dp).semantics { role = Role.RadioButton; selected = chosen },
                shapes = appToggleShapes(ToggleButtonDefaults.shapes(shape = RoundedCornerShape(12.dp))),
                contentPadding = PaddingValues(horizontal = 4.dp), elevation = null,
                colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
            }
        }
    }
}
