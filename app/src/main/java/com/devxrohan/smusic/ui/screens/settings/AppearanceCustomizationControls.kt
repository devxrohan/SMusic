@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.screens.settings

import com.devxrohan.smusic.ui.component.appButtonShapes

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.devxrohan.smusic.customization.*
import com.devxrohan.smusic.ui.component.PreferenceEntry
import com.devxrohan.smusic.ui.component.SettingsGeneralCategory
import androidx.compose.ui.res.painterResource
import com.devxrohan.smusic.R
import com.devxrohan.smusic.utils.dataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Controls share the validated customization registry used for profile import. */
@Composable
fun AdditionalAppearanceControls(lyricsOnly: Boolean = false, group: String? = null, navController: NavController? = null) {
    val context = LocalContext.current
    val repository = remember(context) { CustomizationRepository(context.dataStore) }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<String?>(null) }
    var snapshot by remember { mutableStateOf<CustomizationProfile?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val current by remember(context) { context.dataStore.data.map { CustomizationSchema.snapshot(it) } }.collectAsState(initial = null)
    var resetGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    fun persist(values: Map<String, JsonElement>) {
        if (saving) return
        scope.launch {
            saving = true
            try {
                repository.apply(CustomizationProfile(name = "Settings", values = values), CustomizationCategory.entries.toSet())
                editing = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { message = e.message }
            finally { saving = false }
        }
    }
    val names = if (lyricsOnly) setOf("lyricsTextSizeKey", "lyricsLineSpacingKey", "lyricsScrollKey", "lyricsAlignment", "lyricsOffsetMs")
        else setOf("miniPlayerProgress", "miniPlayerSwipe", "miniPlayerProgressPosition", "homeQuickPicksVisible", "homeKeepListeningVisible", "homeYoutubePicksVisible", "homeRecommendationsVisible", "homeDiscoverVisible", "homeAccountShelvesVisible", "homeForgottenFavoritesVisible", "homeVisibleItems", "homeSectionPriority")
    val settings = CustomizationSchema.settings.filter { it.key in names }
    val groups = if (lyricsOnly) listOf("Lyrics presentation" to settings)
        else listOf("Mini-player" to settings.filter { it.key.startsWith("miniPlayer") },
            "Home layouts & editor" to settings.filter { it.key == "homeSectionPriority" || it.key == "homeVisibleItems" })
    groups.filter { group == null || (group == "home" && it.first == "Home layouts & editor") || (group == "mini" && it.first == "Mini-player") }.forEach { (title, entries) ->
        SettingsGeneralCategory(title = title, items = entries.map { setting ->
            @Composable {
                val value = current?.values?.get(setting.key) ?: JsonNull
                val displayValue = if (value == JsonNull) setting.default else value
                PreferenceEntry(
                    title = { Text(setting.title) },
                    description = setting.describe(displayValue),
                    icon = { Icon(painterResource(if (lyricsOnly) R.drawable.settings_lyrics_rounded else R.drawable.settings_appearance_rounded), null) },
                    isEnabled = current != null && !saving,
                    trailingContent = if (setting.kind == SettingKind.BOOLEAN) ({
                        Switch(checked = (displayValue as JsonPrimitive).boolean,
                            enabled = current != null && !saving,
                            onCheckedChange = { persist(mapOf(setting.key to JsonPrimitive(it))) })
                    }) else null,
                    onClick = {
                        if (setting.kind == SettingKind.BOOLEAN) persist(mapOf(setting.key to JsonPrimitive(!(displayValue as JsonPrimitive).boolean)))
                        else { snapshot = current; editing = setting.key }
                    },
                )
            }
        } + listOf<@Composable () -> Unit>({
            PreferenceEntry(title = { Text("Reset ${if (title == "Home layouts & editor") "Home layouts" else title}") },
                icon = { Icon(painterResource(R.drawable.restore), null) },
                isEnabled = !saving, onClick = { resetGroup = title })
        }))
    }
    resetGroup?.let { title ->
        AlertDialog(onDismissRequest = { resetGroup = null }, title = { Text("Reset $title?") },
            text = { Text("Restore this group's app defaults. Other settings and saved profiles stay unchanged.") },
            confirmButton = { TextButton(shapes = appButtonShapes(), enabled = !saving, onClick = {
                val resetEntries = if (title == "Home layouts & editor") CustomizationSchema.settings.filter { it.key.startsWith("home") || it.key.startsWith("quickPicksCard") } else groups.firstOrNull { it.first == title }?.second.orEmpty()
                persist(resetEntries.associate { it.key to JsonNull })
                resetGroup = null
            }) { Text("Reset") } },
            dismissButton = { TextButton(shapes = appButtonShapes(), onClick = { resetGroup = null }) { Text("Cancel") } })
    }
    message?.let { Text(it) }
    editing?.let { key ->
        val setting = CustomizationSchema.byKey.getValue(key)
        CustomizationSettingDialog(setting, snapshot?.values?.get(key) ?: JsonNull, navController = navController,
            onDismiss = { editing = null }, onChange = { value -> persist(mapOf(key to value)) })
    }
}
