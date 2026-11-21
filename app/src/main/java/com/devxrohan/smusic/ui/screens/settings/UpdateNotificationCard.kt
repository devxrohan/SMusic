@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.component.appButtonShapes

@Composable
internal fun UpdateNotificationCard(version: String, onOpen: () -> Unit) {
    val badge = MaterialShapes.Clover4Leaf.toShape()
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, badge),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.deployed_code_update), null, Modifier.size(27.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.update_notification_title), style = MaterialTheme.typography.titleLarge)
                    Text("SMusic $version", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(stringResource(R.string.update_notification_open_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onOpen, shapes = appButtonShapes(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_notification_open))
            }
        }
    }
}
