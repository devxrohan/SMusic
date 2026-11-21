package com.devxrohan.smusic.ui.component

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.devxrohan.smusic.R
import com.devxrohan.smusic.utils.rememberPreference
import org.json.JSONObject
import kotlinx.coroutines.launch

internal val SpeedDialCollectionsKey = stringSetPreferencesKey("homeSpeedDialCollections")
internal val ArtworkThemeRefreshKey = androidx.datastore.preferences.core.intPreferencesKey("artworkThemeRefresh")

internal data class SpeedDialCollection(val route: String, val title: String, val artwork: String?) {
    fun encode(): String = JSONObject().put("route", route).put("title", title).put("artwork", artwork).toString()
    companion object {
        fun decode(raw: String): SpeedDialCollection? = runCatching {
            val json = JSONObject(raw)
            val route = json.getString("route")
            require(listOf("album/", "artist/", "local_playlist/", "online_playlist/").any(route::startsWith))
            SpeedDialCollection(route, json.getString("title"), json.optString("artwork").takeIf { it.isNotBlank() && it != "null" })
        }.getOrNull()
    }
}

internal data class SpeedDialPinAction(
    val pinned: Boolean, val busy: Boolean, val error: String?, val toggle: () -> Unit,
)

@Composable
private fun rememberSpeedDialPinAction(
    key: String, collection: SpeedDialCollection?, onDismiss: () -> Unit, beforePin: () -> Unit = {},
): SpeedDialPinAction {
    val songs by rememberPreference(SpeedDialSongsKey, emptySet())
    val collections by rememberPreference(SpeedDialCollectionsKey, emptySet())
    val stored = if (collection == null) key.removePrefix("song:") in songs
        else collections.any { SpeedDialCollection.decode(it)?.route == key }
    var completed by remember(key) { mutableStateOf<Boolean?>(null) }
    var busy by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    val reduced by rememberPreference(com.devxrohan.smusic.constants.DisableAnimationsKey, false)
    val dismiss by rememberUpdatedState(onDismiss)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    return SpeedDialPinAction(completed ?: stored, busy, error) {
        if (!busy) scope.launch {
            busy = true
            error = null
            try {
                if (!stored) beforePin()
                completed = context.toggleSpeedDialPin(key, collection)
                if (!reduced) kotlinx.coroutines.delay(220)
                dismiss()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Try again" }
            finally { busy = false }
        }
    }
}

@Composable
internal fun rememberSpeedDialCollectionPin(route: String, title: String, artwork: String?, onDismiss: () -> Unit): SpeedDialPinAction =
    rememberSpeedDialPinAction(route, SpeedDialCollection(route, title, artwork), onDismiss)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpeedDialPinIcon(pinned: Boolean, failed: Boolean = false) {
    val reduced by rememberPreference(com.devxrohan.smusic.constants.DisableAnimationsKey, false)
    val angle by androidx.compose.animation.core.animateFloatAsState(
        if (pinned) 14f else 0f,
        if (reduced) androidx.compose.animation.core.snap() else MaterialTheme.motionScheme.fastSpatialSpec(), label = "pinAngle")
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pinned) 1.14f else 1f,
        if (reduced) androidx.compose.animation.core.snap() else MaterialTheme.motionScheme.fastSpatialSpec(), label = "pinScale")
    val color by androidx.compose.animation.animateColorAsState(
        if (failed) MaterialTheme.colorScheme.error else if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        if (reduced) androidx.compose.animation.core.snap() else MaterialTheme.motionScheme.fastEffectsSpec(), label = "pinColor")
    Icon(painterResource(R.drawable.speed_dial_pin),
        if (failed) "Could not update pin" else if (pinned) "Pinned to Speed dial" else "Pin to Speed dial",
        Modifier.graphicsLayer { rotationZ = angle; scaleX = scale; scaleY = scale }, tint = color)
}

@Composable
internal fun SpeedDialCollectionPin(route: String, title: String, artwork: String?, modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    val action = rememberSpeedDialCollectionPin(route, title, artwork, onDismiss)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.foundation.layout.Column(
        modifier = modifier.pressFeedback(interaction)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(enabled = !action.busy, interactionSource = interaction, indication = ripple(), onClick = action.toggle)
            .padding(12.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        SpeedDialPinIcon(action.pinned, action.error != null)
        Text(action.error ?: "Speed dial", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun rememberSpeedDialSongPin(id: String, onDismiss: () -> Unit, beforePin: () -> Unit = {}): SpeedDialPinAction =
    rememberSpeedDialPinAction("song:$id", null, onDismiss, beforePin)

@Composable
internal fun SpeedDialSongPin(id: String, onDismiss: () -> Unit, beforePin: () -> Unit = {}) {
    val action = rememberSpeedDialSongPin(id, onDismiss, beforePin)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val errorText = action.error
    val supporting: (@Composable () -> Unit)? = if (errorText == null) null else {
        { Text(errorText, color = MaterialTheme.colorScheme.error) }
    }
    ListItem(
        headlineContent = { Text(if (action.pinned) "Remove from Speed dial" else "Add to Speed dial") },
        supportingContent = supporting,
        leadingContent = { SpeedDialPinIcon(action.pinned, action.error != null) },
        modifier = Modifier.pressFeedback(interaction).clickable(enabled = !action.busy, interactionSource = interaction,
            indication = ripple(), onClick = action.toggle),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}
