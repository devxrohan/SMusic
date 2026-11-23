@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.TouchHapticsStrengthKey
import com.devxrohan.smusic.ui.component.DottedSlider
import com.devxrohan.smusic.ui.component.SwitchPreference
import com.devxrohan.smusic.ui.component.appButtonShapes
import com.devxrohan.smusic.ui.component.touchHaptic
import com.devxrohan.smusic.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
internal fun HapticFeedbackSettings(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val (strength, changeStrength) = rememberPreference(TouchHapticsStrengthKey, 60)
    var preview by remember(strength) { mutableFloatStateOf(strength.toFloat()) }
    Column {
        SwitchPreference(
            title = { Text("Touch feedback") },
            description = "Vibration for controls and settings sliders.",
            icon = { Icon(painterResource(R.drawable.tune), null) },
            checked = enabled, onCheckedChange = onEnabledChange,
        )
        AnimatedVisibility(enabled) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Haptic strength", style = MaterialTheme.typography.titleSmall)
                    Text("${preview.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                }
                DottedSlider(value = preview, onValueChange = { preview = it },
                    valueRange = 0f..100f, steps = 19,
                    onValueChangeFinished = {
                        changeStrength(preview.roundToInt())
                        context.touchHaptic()
                    })
                Text("Adjust the feel for your phone. Player seeking stays silent.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(shapes = appButtonShapes(), onClick = { context.touchHaptic() }, enabled = strength > 0) {
                    Text("Test feedback")
                }
            }
        }
    }
}
