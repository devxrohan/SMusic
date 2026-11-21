@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.devxrohan.smusic.ui.component.ListDialog

/** Shared single-choice picker with one accessible selection target per option. */
@Composable
internal fun SettingsChoiceDialog(title: String, selected: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    ListDialog(onDismiss = onDismiss) {
        item { Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) }
        item {
            Column(Modifier.padding(horizontal = 16.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { (value, label) ->
                    val active = value == selected
                    com.devxrohan.smusic.ui.component.SingleChoiceOption(label, active, onSelect = { onSelect(value) })
                }
            }
        }
    }
}
