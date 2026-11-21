@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.component.DottedSlider as Slider

import com.devxrohan.smusic.ui.component.appButtonShapes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.customization.*
import com.devxrohan.smusic.ui.component.*
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun LyricsAppearanceEditor() {
    val context = LocalContext.current
    val savedStyle by rememberPreference(LyricsColorStyleKey, "THEME")
    val savedFirst by rememberPreference(LyricsColorPrimaryKey, "FFFFFF")
    val savedSecond by rememberPreference(LyricsColorSecondaryKey, "C084FC")
    val size by rememberPreference(LyricsTextSizeKey, 26f)
    val spacing by rememberPreference(LyricsLineSpacingKey, 1.3f)
    val alignment by rememberPreference(LyricsAlignmentKey, "AUTO")
    var open by rememberSaveable { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    SettingsGeneralCategory(title = "Lyrics colours", items = listOf({
        PreferenceEntry(title = { Text("Colours & preview") },
            description = when (savedStyle) { "SOLID" -> "Single colour"; "GRADIENT" -> "Gradient"; else -> "Match app theme" },
            icon = { Icon(painterResource(R.drawable.palette), null) }, onClick = { open = true })
    }, {
        PreferenceEntry(title = { Text("Reset lyrics colours") }, icon = { Icon(painterResource(R.drawable.restore), null) }, onClick = { reset = true })
    }))
    if (reset) AlertDialog(onDismissRequest = { reset = false }, title = { Text("Reset lyrics colours?") },
        confirmButton = { TextButton(shapes = appButtonShapes(), onClick = {
            scope.launch {
                try { CustomizationRepository(context.dataStore).apply(CustomizationProfile(name = "Lyrics colours", values = mapOf(LyricsColorStyleKey.name to JsonNull, LyricsColorPrimaryKey.name to JsonNull, LyricsColorSecondaryKey.name to JsonNull)), setOf(CustomizationCategory.LYRICS)); reset = false }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { error = e.message }
            }
        }) { Text("Reset") } }, dismissButton = { TextButton(shapes = appButtonShapes(), onClick = { reset = false }) { Text("Cancel") } })
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (open) LyricsColoursDialog(savedStyle, savedFirst, savedSecond, size, spacing, alignment, onDismiss = { open = false })
}

@Composable
private fun LyricsColoursDialog(savedStyle: String, savedFirst: String, savedSecond: String, size: Float, spacing: Float, alignment: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var style by rememberSaveable { mutableStateOf(savedStyle) }
    var first by rememberSaveable { mutableStateOf(savedFirst) }
    var second by rememberSaveable { mutableStateOf(savedSecond) }
    var choosing by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ink = remember(style, first, second) { LyricsInkPresets.create(style, first, second) }
    val align = when (alignment) { "START" -> TextAlign.Start; "END" -> TextAlign.End; else -> TextAlign.Center }
    val modeLabels = listOf("THEME" to "Match app theme", "SOLID" to "Single colour", "GRADIENT" to "Gradient")
    val selectedPreset = LyricsInkPresets.all.firstOrNull { first == it.first && second == it.second && style == if (it.gradient) "GRADIENT" else "SOLID" }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.94f).padding(horizontal = 12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Lyrics colours", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.close), "Close") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Surface(Modifier.padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Let the quiet turn to light", modifier = Modifier.fillMaxWidth().alpha(.35f).lyricsInk(ink), textAlign = align, fontSize = (size * .78f).sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Every moment has a melody", modifier = Modifier.fillMaxWidth().lyricsInk(ink), textAlign = align, fontSize = size.sp, lineHeight = (size * spacing).sp, fontWeight = FontWeight.Bold)
                            Text("Take the music home with you", modifier = Modifier.fillMaxWidth().alpha(.35f).lyricsInk(ink), textAlign = align, fontSize = (size * .78f).sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    SettingsGeneralCategory(title = "Appearance", items = listOf({
                        PreferenceEntry(title = { Text("Colour style") }, description = modeLabels.first { it.first == style }.second, icon = { Icon(painterResource(R.drawable.palette), null) }, onClick = { choosing = "mode" })
                    }, {
                        PreferenceEntry(title = { Text("Colour presets") }, description = if (style == "THEME") "Match app theme" else selectedPreset?.name ?: "Custom colours", icon = { Icon(painterResource(R.drawable.settings_appearance_rounded), null) }, onClick = { choosing = "preset" })
                    }))
                    if (style != "THEME") SettingsGeneralCategory(title = "Custom colours", items = listOf<@Composable () -> Unit>({
                        ColourPreference(if (style == "GRADIENT") "First colour" else "Text colour", first) { choosing = "first" }
                    }) + if (style == "GRADIENT") listOf<@Composable () -> Unit>({ ColourPreference("Second colour", second) { choosing = "second" } }) else emptyList())
                    error?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(shapes = appButtonShapes(), onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(shapes = appButtonShapes(), enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                CustomizationRepository(context.dataStore).apply(CustomizationProfile(name = "Lyrics colours", values = mapOf(LyricsColorStyleKey.name to JsonPrimitive(style), LyricsColorPrimaryKey.name to JsonPrimitive(first), LyricsColorSecondaryKey.name to JsonPrimitive(second))), setOf(CustomizationCategory.LYRICS))
                                onDismiss()
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { error = e.message }
                            finally { busy = false }
                        }
                    }) { Text(if (busy) "Applying…" else "Apply colours") }
                }
            }
        }
    }
    when (choosing) {
        "mode" -> SettingsChoiceDialog("Colour style", style, modeLabels, { choosing = null }) { style = it; choosing = null }
        "preset" -> ListDialog(onDismiss = { choosing = null }) {
            item { Text("Colour presets", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
            items(LyricsInkPresets.all, key = { it.name }) { preset ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // ListPreference shares these radio rows with the original Dark theme picker.
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(preset.name) },
                        leadingContent = { RadioButton(selected = selectedPreset == preset, onClick = null) },
                        trailingContent = { Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(hexColour(preset.first), hexColour(preset.second))))) },
                        modifier = Modifier.clickable { style = if (preset.gradient) "GRADIENT" else "SOLID"; first = preset.first; second = preset.second; choosing = null },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        "first", "second" -> VisualColourDialog(if (choosing == "first") first else second, { choosing = null }) { colour ->
            if (choosing == "first") first = colour else second = colour
            choosing = null
        }
    }
}

private fun hexColour(hex: String): Color = Color(android.graphics.Color.parseColor("#${hex.takeIf { Regex("[A-Fa-f0-9]{6}").matches(it) } ?: "FFFFFF"}"))

@Composable
private fun ColourPreference(title: String, hex: String, onClick: () -> Unit) {
    PreferenceEntry(title = { Text(title) }, description = "#$hex", icon = { Box(Modifier.size(28.dp).clip(CircleShape).background(hexColour(hex))) }, onClick = onClick)
}

/** Full HSV selector with a saturation/value surface, hue spectrum and optional exact hex input. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualColourDialog(initial: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor("#$initial"), it) } }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(start[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(start[2]) }
    fun encode(): String = String.format(java.util.Locale.ROOT, "%06X", android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)) and 0xFFFFFF)
    var hex by rememberSaveable { mutableStateOf(initial) }
    val spectrum = remember { listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose colour") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Canvas(Modifier.semantics { contentDescription = "Saturation and brightness" }.fillMaxWidth().height(180.dp).clip(MaterialTheme.shapes.medium)
                .pointerInput(hue) { detectTapGestures { point -> saturation = (point.x / size.width).coerceIn(0f, 1f); brightness = 1f - (point.y / size.height).coerceIn(0f, 1f); hex = encode() } }
                .pointerInput(hue) { detectDragGestures(onDragStart = { point -> saturation = (point.x / size.width).coerceIn(0f, 1f); brightness = 1f - (point.y / size.height).coerceIn(0f, 1f); hex = encode() }) { change, _ -> change.consume(); saturation = (change.position.x / size.width).coerceIn(0f, 1f); brightness = 1f - (change.position.y / size.height).coerceIn(0f, 1f); hex = encode() } }) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val point = Offset(saturation * size.width, (1f - brightness) * size.height)
                drawCircle(Color.Black.copy(alpha = .6f), radius = 9.dp.toPx(), center = point, style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
                drawCircle(Color.White, radius = 9.dp.toPx(), center = point, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            Text("Hue", style = MaterialTheme.typography.labelLarge)
            Slider(modifier = Modifier.semantics { contentDescription = "Hue" }, value = hue, onValueChange = { hue = it; hex = encode() }, valueRange = 0f..359.9f,
                track = { Canvas(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) { drawRect(Brush.horizontalGradient(spectrum)) } })
            Text("Saturation", style = MaterialTheme.typography.labelLarge)
            Slider(modifier = Modifier.semantics { contentDescription = "Saturation" }, value = saturation,
                onValueChange = { saturation = it; hex = encode() })
            Text("Brightness", style = MaterialTheme.typography.labelLarge)
            Slider(modifier = Modifier.semantics { contentDescription = "Brightness" }, value = brightness,
                onValueChange = { brightness = it; hex = encode() })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.medium).background(Color.hsv(hue, saturation, brightness)))
                OutlinedTextField(value = hex, onValueChange = { value ->
                    hex = value.removePrefix("#").take(6).uppercase()
                    if (Regex("[A-Fa-f0-9]{6}").matches(hex)) {
                        val hsv = FloatArray(3); android.graphics.Color.colorToHSV(android.graphics.Color.parseColor("#$hex"), hsv)
                        hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                    }
                }, label = { Text("Hex colour") }, prefix = { Text("#") }, singleLine = true, modifier = Modifier.weight(1f), isError = !Regex("[A-Fa-f0-9]{6}").matches(hex))
            }
        }
    }, confirmButton = { TextButton(shapes = appButtonShapes(), enabled = Regex("[A-Fa-f0-9]{6}").matches(hex), onClick = { onSelect(hex) }) { Text("Use colour") } }, dismissButton = { TextButton(shapes = appButtonShapes(), onClick = onDismiss) { Text("Cancel") } })
}
