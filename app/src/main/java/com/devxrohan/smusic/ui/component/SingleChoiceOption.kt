@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.utils.rememberPreference

/** Native interactive choices share shape feedback, radio semantics and one click target. */
@Composable
internal fun SingleChoiceOption(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    val resting = RoundedCornerShape(16.dp)
    val scheme = MaterialTheme.colorScheme
    ListItem(
        selected = selected,
        onClick = onSelect,
        modifier = modifier.fillMaxWidth().semantics { role = Role.RadioButton },
        shapes = ListItemDefaults.shapes(
            shape = resting,
            selectedShape = if (reduceMotion) resting else RoundedCornerShape(28.dp),
            pressedShape = if (reduceMotion) resting else RoundedCornerShape(32.dp),
            focusedShape = resting,
            hoveredShape = resting,
            draggedShape = resting,
        ),
        colors = ListItemDefaults.colors(
            containerColor = scheme.surfaceContainerLow,
            selectedContainerColor = scheme.secondaryContainer,
            selectedContentColor = scheme.onSecondaryContainer,
        ),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
