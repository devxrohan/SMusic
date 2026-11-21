@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Compact preset selection: a soft outline, a clear selected state and press-shape feedback. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressivePreset(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton; this.selected = selected },
        shapes = appToggleShapes(ToggleButtonDefaults.shapes(shape = CircleShape, pressedShape = MaterialTheme.shapes.large, checkedShape = CircleShape)),
        elevation = null,
        border = if (selected) null else BorderStroke(1.dp, scheme.outlineVariant),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = scheme.surface,
            contentColor = scheme.onSurfaceVariant,
            checkedContainerColor = scheme.secondaryContainer,
            checkedContentColor = scheme.onSecondaryContainer,
        ),
    ) { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) }
}
