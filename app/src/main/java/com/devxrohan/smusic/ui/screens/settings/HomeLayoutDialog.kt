@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.component.appButtonShapes
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.devxrohan.smusic.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.devxrohan.smusic.ui.component.AppIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.devxrohan.smusic.customization.CustomizationSchema
import com.devxrohan.smusic.customization.HomeLayout
import com.devxrohan.smusic.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.devxrohan.smusic.customization.*
import kotlinx.serialization.json.*

/** Draft-only rearrangement. The caller commits only when Apply is pressed. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeLayoutDialog(current: JsonElement, onDismiss: () -> Unit, navController: NavController? = null) {
    var encodedOrder by rememberSaveable { mutableStateOf(HomeLayout.encode(HomeLayout.decode((current as? JsonPrimitive)?.content))) }
    var presetValues by rememberSaveable { mutableStateOf("{}") }
    var presetName by rememberSaveable { mutableStateOf("Custom") }
    var draftEdited by rememberSaveable { mutableStateOf(false) }
    val overrides = remember(presetValues) { Json.parseToJsonElement(presetValues).jsonObject }
    var saving by remember { mutableStateOf(false) }
    val reduceMotion by com.devxrohan.smusic.utils.rememberPreference(com.devxrohan.smusic.constants.DisableAnimationsKey, false)
    val motion = MaterialTheme.motionScheme
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val order = HomeLayout.decode(encodedOrder)
    val rowHeights = remember { mutableStateMapOf<String, Int>() }
    var preview by rememberSaveable { mutableStateOf(false) }
    var fullPreview by remember { mutableStateOf(false) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val context = LocalContext.current
    val profile by remember(context) { context.dataStore.data.map { CustomizationSchema.snapshot(it) } }.collectAsState(initial = null)
    LaunchedEffect(profile) {
        val loaded = profile ?: return@LaunchedEffect
        if (!draftEdited) {
            presetName = HomeLayout.presets.firstOrNull { preset ->
                preset.values.all { (key, expected) ->
                    val raw = loaded.values[key]
                    val actual = raw?.takeUnless { it == JsonNull } ?: CustomizationSchema.byKey[key]?.default
                    if (key == "homeSectionPriority") HomeLayout.decode(actual?.jsonPrimitive?.content) == HomeLayout.decode(expected.jsonPrimitive.content)
                    else actual == expected
                }
            }?.name ?: "Custom"
        }
    }
    fun value(key: String): JsonElement? = overrides[key] ?: profile?.values?.get(key)
    fun visible(id: String): Boolean = (value(HomeLayout.visibilityKeys.getValue(id)) as? JsonPrimitive)?.booleanOrNull ?: true
    fun move(id: String, delta: Int) {
        if (saving) return
        val list = HomeLayout.decode(encodedOrder).toMutableList()
        val from = list.indexOf(id)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from >= 0 && from != to) { list.add(to, list.removeAt(from)); encodedOrder = HomeLayout.encode(list); presetName = "Custom"; draftEdited = true }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.94f).padding(horizontal = 12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Home layouts", style = MaterialTheme.typography.headlineSmall)
                        Text(if (presetName == "Custom") "Your custom arrangement" else "$presetName layout", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeLayout.presets.forEach { preset ->
                        val selected = presetName == preset.name
                        Surface(Modifier.weight(1f).clip(if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large).selectable(selected = selected, enabled = !saving, role = Role.RadioButton, onClick = {
                            draftEdited = true
                            presetName = preset.name
                            presetValues = JsonObject(preset.values).toString()
                            encodedOrder = preset.values.getValue("homeSectionPriority").jsonPrimitive.content
                            preview = true
                        }), shape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(painterResource(if (preset.name == "Classic") R.drawable.home else R.drawable.history), null, tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (preset.name == "Classic") "All your music, together" else "Favourites. Fewer distractions.", style = MaterialTheme.typography.bodySmall, minLines = 2)
                                val firstSection = HomeLayout.decode(preset.values["homeSectionPriority"]?.jsonPrimitive?.content).first()
                                val tracks = HomePreviewCatalog.sections[firstSection].orEmpty().ifEmpty { HomePreviewCatalog.sections.values.flatten().distinctBy { it.id } }.take(3)
                                if (tracks.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    tracks.forEach { track ->
                                        AsyncImage(track.thumbnail, null, contentScale = ContentScale.Crop,
                                            placeholder = painterResource(R.drawable.music_note), error = painterResource(R.drawable.music_note),
                                            modifier = Modifier.weight(1f).aspectRatio(1f).clip(MaterialTheme.shapes.small))
                                    }
                                }
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                com.devxrohan.smusic.ui.component.ExpressiveChoiceRow(listOf("Arrange", "Preview"), if (preview) 1 else 0, { preview = it == 1 }, enabled = !saving)
                AnimatedContent(
                    targetState = preview && navController != null,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        if (reduceMotion) ContentTransform(EnterTransition.None, ExitTransition.None)
                        else (fadeIn(motion.defaultEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec()) { if (targetState) it / 10 else -it / 10 }) togetherWith
                            (fadeOut(motion.defaultEffectsSpec()) + slideOutHorizontally(motion.defaultSpatialSpec()) { if (targetState) -it / 10 else it / 10 })
                    },
                    label = "homeEditorPanel",
                ) { showingPreview ->
                    if (showingPreview && navController != null) {
                        Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large)) {
                            Box(Modifier.fillMaxSize().blur(8.dp)) {
                                HomeContentPreview(profile?.values.orEmpty() + overrides + ("homeSectionPriority" to JsonPrimitive(encodedOrder)), navController)
                            }
                            Surface(onClick = { fullPreview = true }, modifier = Modifier.fillMaxSize(),
                                shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.scrim.copy(alpha = .36f)) {
                                Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 6.dp) {
                                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.fullscreen), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text("Tap to full screen", style = MaterialTheme.typography.titleLarge, color = androidx.compose.ui.graphics.Color.White)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Explore your Home layout", style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = .85f))
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Hold the handle to reorder sections", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            LazyColumn(Modifier.weight(1f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)) {
                                itemsIndexed(order, key = { _, id -> id }) { index, id ->
                                    var more by remember { mutableStateOf(false) }
                                    val rowThreshold = rowHeights[id]?.toFloat() ?: threshold
                                    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).onSizeChanged { rowHeights[id] = it.height }.background(if (draggedId == id) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(48.dp).semantics { contentDescription = "Drag ${HomeLayout.sections.getValue(id)} to reorder" }
                                            .pointerInput(id, rowThreshold) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { draggedId = id; dragDistance = 0f },
                                                    onDragEnd = { draggedId = null; dragDistance = 0f },
                                                    onDragCancel = { draggedId = null; dragDistance = 0f },
                                                    onDrag = { change, amount ->
                                                        change.consume(); dragDistance += amount.y
                                                        while (dragDistance >= rowThreshold) { move(id, 1); dragDistance -= rowThreshold }
                                                        while (dragDistance <= -rowThreshold) { move(id, -1); dragDistance += rowThreshold }
                                                    })
                                            }, contentAlignment = Alignment.Center) {
                                            Icon(painterResource(R.drawable.drag_handle_rounded), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                        }
                                        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                            Text(HomeLayout.sections.getValue(id), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                        }
                                        Switch(checked = visible(id), enabled = !saving, onCheckedChange = { enabled ->
                                            presetValues = JsonObject(overrides.toMap() + (HomeLayout.visibilityKeys.getValue(id) to JsonPrimitive(enabled))).toString()
                                            presetName = "Custom"; draftEdited = true
                                        })
                                        Box {
                                            IconButton(onClick = { more = true }, modifier = Modifier.size(48.dp)) { Icon(painterResource(R.drawable.more_vert), "Move section", modifier = Modifier.size(24.dp)) }
                                            DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                                DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(painterResource(R.drawable.arrow_up_rounded), null) }, enabled = index > 0, onClick = { move(id, -1); more = false })
                                                DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(painterResource(R.drawable.arrow_down_rounded), null) }, enabled = index < order.lastIndex, onClick = { move(id, 1); more = false })
                                            }
                                        }
                                    }
                                    if (index < order.lastIndex) HorizontalDivider(Modifier.padding(start = 54.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f))
                                }
                                item {
                                    TextButton(onClick = { encodedOrder = HomeLayout.encode(HomeLayout.defaultOrder); presetName = "Custom"; draftEdited = true }, modifier = Modifier.padding(8.dp)) {
                                        Icon(painterResource(R.drawable.restore), null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reset order")
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(enabled = !saving, shapes = appButtonShapes(), onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val values = overrides.toMap() + ("homeSectionPriority" to JsonPrimitive(encodedOrder))
                                CustomizationRepository(context.dataStore).apply(CustomizationProfile(name = "Home layout", values = values), setOf(CustomizationCategory.LAYOUT))
                                onDismiss()
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "Could not apply layout" }
                            finally { saving = false }
                        }
                    }) { Text(if (saving) "Applying…" else "Apply layout") }
                }
            }
        }
    }
    if (fullPreview && navController != null) Dialog(onDismissRequest = { fullPreview = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { fullPreview = false }) { Icon(painterResource(R.drawable.arrow_back), "Back to layouts") }
                    Text("Home preview", style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.weight(1f)) { HomeContentPreview(profile?.values.orEmpty() + overrides + ("homeSectionPriority" to JsonPrimitive(encodedOrder)), navController) }
            }
        }
    }

}
