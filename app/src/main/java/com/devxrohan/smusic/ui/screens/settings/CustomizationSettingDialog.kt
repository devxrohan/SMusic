@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.component.DottedSlider as Slider

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.customization.*
import com.devxrohan.smusic.ui.component.appButtonShapes
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomizationSettingDialog(setting: CustomizationSetting, current: JsonElement, navController: NavController? = null, onDismiss: () -> Unit, onChange: (JsonElement) -> Unit) {
    if (setting.key == HomeSectionPriorityKey.name) {
        HomeLayoutDialog(current, onDismiss, navController)
        return
    }
    var number by remember(setting.key) { mutableFloatStateOf((current as? JsonPrimitive)?.floatOrNull ?: setting.default.floatOrNull ?: 0f) }
    var numberText by remember(setting.key) { mutableStateOf(number.toString()) }
    val parsedNumber = numberText.toFloatOrNull()?.takeIf { it.isFinite() && setting.range?.contains(it) == true }

    if (setting.kind != SettingKind.NUMBER) {
        val selected = (current.takeUnless { it == JsonNull } ?: setting.default).jsonPrimitive.content
        val options = if (setting.kind == SettingKind.BOOLEAN) listOf("true" to "On", "false" to "Off") else setting.choices.toList()
        SettingsChoiceDialog(setting.title, selected, options, onDismiss) { value ->
            onChange(if (value == setting.default.content) JsonNull else if (setting.kind == SettingKind.BOOLEAN) JsonPrimitive(value.toBoolean()) else JsonPrimitive(value))
        }
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(setting.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (setting.kind) {
                SettingKind.NUMBER -> {
                    OutlinedTextField(
                        value = numberText,
                        onValueChange = { numberText = it; it.toFloatOrNull()?.takeIf { v -> v.isFinite() && v in setting.range!! }?.let { number = it } },
                        label = { Text("Value") },
                        supportingText = { Text("${setting.range!!.start} to ${setting.range.endInclusive}") },
                        isError = parsedNumber == null,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                    Slider(value = number.coerceIn(setting.range!!), onValueChange = { number = it; numberText = it.toString() }, valueRange = setting.range!!)
                    if (setting.key == LyricsTextSizeKey.name || setting.key == LyricsLineSpacingKey.name) {
                        Text("Every moment has a melody", fontSize = (if (setting.key == LyricsTextSizeKey.name) number else 26f).sp,
                            lineHeight = (if (setting.key == LyricsLineSpacingKey.name) 26f * number else number * 1.3f).sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                    }

                }
                else -> Unit
            }
            TextButton(shapes = appButtonShapes(), onClick = { onChange(JsonNull) }) { Text("Reset to default") }
        }
    }, confirmButton = {
        if (setting.kind == SettingKind.NUMBER) TextButton(shapes = appButtonShapes(), enabled = parsedNumber != null, onClick = { parsedNumber?.let { onChange(JsonPrimitive(it)) } }) { Text("Use value") }
    }, dismissButton = { TextButton(shapes = appButtonShapes(), onClick = onDismiss) { Text("Cancel") } })
}
