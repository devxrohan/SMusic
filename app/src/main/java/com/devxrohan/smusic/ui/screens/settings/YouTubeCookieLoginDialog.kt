package com.devxrohan.smusic.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.devxrohan.smusic.R
import com.devxrohan.smusic.constants.*
import com.devxrohan.smusic.innertube.YouTube
import com.devxrohan.smusic.innertube.utils.parseCookieString
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.dataStoreCache
import com.devxrohan.smusic.utils.rememberPreference
import kotlinx.coroutines.*

@Composable
internal fun YouTubeCookieLoginDialog(onDismiss: () -> Unit, onConnected: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visitorData by rememberPreference(VisitorDataKey, "")
    var cookieInput by remember { mutableStateOf("") }
    var visitorInput by remember { mutableStateOf(visitorData) }
    var saving by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!saving) onDismiss() },
            title = { Text(stringResource(R.string.ui_advanced_youtube_login)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(stringResource(R.string.ui_paste_cookies_copied_from_music_youtube))
                    OutlinedTextField(
                        value = cookieInput,
                        enabled = !saving,
                        onValueChange = { cookieInput = it },
                        label = { Text(stringResource(R.string.ui_cookie_string)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 3,
                        maxLines = 6,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    OutlinedTextField(
                        value = visitorInput,
                        enabled = !saving,
                        onValueChange = { visitorInput = it },
                        label = { Text(stringResource(R.string.ui_visitor_data_optional)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    val parsed = parseCookieString(cookieInput.trim())
                    if (cookieInput.isBlank() || "SAPISID" !in parsed) {
                        Toast.makeText(context, "Invalid cookie: SAPISID was not found", Toast.LENGTH_LONG).show()
                    } else {
                        val cookieToSave = cookieInput.trim()
                        val visitorToSave = visitorInput.trim()
                        saving = true
                        scope.launch {
                            val oldCookie = YouTube.cookie
                            try {
                                YouTube.cookie = cookieToSave
                                val info = withTimeout(30000L) {
                                    var profile = withContext(Dispatchers.IO) { YouTube.accountInfo().getOrNull() }
                                    repeat(2) {
                                        if (profile == null) {
                                            delay(900L)
                                            profile = withContext(Dispatchers.IO) { YouTube.accountInfo().getOrNull() }
                                        }
                                    }
                                    profile
                                }
                                if (info == null) {
                                    YouTube.cookie = oldCookie
                                    Toast.makeText(context, "Could not verify this session. Check your cookie and connection.", Toast.LENGTH_LONG).show()
                                } else {
                                    val photo = info.photoUrl?.takeIf { it.startsWith("https://") }.orEmpty()
                                    context.dataStore.edit {
                                        it[InnerTubeCookieKey] = cookieToSave
                                        it[VisitorDataKey] = visitorToSave
                                        it[AccountNameKey] = info.name
                                        it[AccountEmailKey] = info.email.orEmpty()
                                        it[AccountChannelHandleKey] = info.channelHandle.orEmpty()
                                        it[AccountPhotoUrlKey] = photo
                                    }
                                    dataStoreCache[InnerTubeCookieKey.name] = cookieToSave
                                    dataStoreCache[VisitorDataKey.name] = visitorToSave
                                    dataStoreCache[AccountNameKey.name] = info.name
                                    dataStoreCache[AccountEmailKey.name] = info.email.orEmpty()
                                    dataStoreCache[AccountChannelHandleKey.name] = info.channelHandle.orEmpty()
                                    dataStoreCache[AccountPhotoUrlKey.name] = photo
                                    onConnected()
                                }
                            } catch (_: TimeoutCancellationException) {
                                YouTube.cookie = oldCookie
                                Toast.makeText(context, "Connection timed out. Please try again.", Toast.LENGTH_LONG).show()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                YouTube.cookie = oldCookie
                                throw cancelled
                            } catch (_: Exception) {
                                YouTube.cookie = oldCookie
                                Toast.makeText(context, "Could not connect. Please try again.", Toast.LENGTH_LONG).show()
                            } finally { saving = false }
                        }
                    }
                }) { Text(if (saving) "Connecting…" else stringResource(R.string.ui_save_session)) }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
}
