package com.devxrohan.smusic.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/** Android's system Photo Picker followed by SMusic's lightweight circular crop editor. */
@Composable
fun rememberGuestProfilePhotoPicker(onImageSaved: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnImageSaved = rememberUpdatedState(onImageSaved)
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true
                selectedBitmap = withContext(Dispatchers.IO) { decodePickedImage(context, uri) }
                loading = false
            }
        }
    }

    if (loading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Loading photo") },
            text = { Text("Please wait while your photo is prepared.") },
            confirmButton = {},
        )
    }

    selectedBitmap?.let { bitmap ->
        CircularAvatarCropDialog(
            bitmap = bitmap,
            onDismiss = { selectedBitmap = null },
            onSave = { cropRect ->
                scope.launch {
                    val path = withContext(Dispatchers.IO) { saveCroppedGuestAvatar(context, bitmap, cropRect) }
                    selectedBitmap = null
                    if (path != null) latestOnImageSaved.value(path)
                }
            },
        )
    }

    return remember(picker) {
        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

@Composable
private fun CircularAvatarCropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onSave: (Rect) -> Unit,
) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var panX by remember(bitmap) { mutableFloatStateOf(0f) }
    var panY by remember(bitmap) { mutableFloatStateOf(0f) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF171717), androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Adjust profile photo", color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Pinch to zoom and drag to reposition", color = Color.LightGray)
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF303030))
                    .border(2.dp, Color.White, CircleShape)
                    .onSizeChanged { viewport = it }
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, pan, zoomChange, _ ->
                            zoom = (zoom * zoomChange).coerceIn(1f, 6f)
                            panX += pan.x
                            panY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Profile photo crop preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX
                            translationY = panY
                        },
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    zoom = 1f
                    panX = 0f
                    panY = 0f
                }) { Text("Reset") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        enabled = viewport.width > 0 && viewport.height > 0,
                        onClick = {
                            onSave(calculateCropRect(bitmap, viewport, zoom, panX, panY))
                        },
                    ) { Text("Use photo") }
                }
            }
        }
    }
}

private fun calculateCropRect(
    bitmap: Bitmap,
    viewport: IntSize,
    zoom: Float,
    panX: Float,
    panY: Float,
): Rect {
    val vw = viewport.width.toFloat()
    val vh = viewport.height.toFloat()
    val base = min(vw / bitmap.width, vh / bitmap.height)
    val renderedW = bitmap.width * base
    val renderedH = bitmap.height * base
    val imageLeft = (vw - renderedW) / 2f
    val imageTop = (vh - renderedH) / 2f

    fun sourceX(screenX: Float) = ((screenX - vw / 2f - panX) / zoom + vw / 2f - imageLeft) / base
    fun sourceY(screenY: Float) = ((screenY - vh / 2f - panY) / zoom + vh / 2f - imageTop) / base

    val leftF = sourceX(0f)
    val topF = sourceY(0f)
    val rightF = sourceX(vw)
    val bottomF = sourceY(vh)
    val size = max(1, min(
        min(bitmap.width, bitmap.height),
        min((rightF - leftF).toInt(), (bottomF - topF).toInt()).coerceAtLeast(1),
    ))
    val left = leftF.toInt().coerceIn(0, bitmap.width - size)
    val top = topF.toInt().coerceIn(0, bitmap.height - size)
    return Rect(left, top, left + size, top + size)
}

private fun decodePickedImage(context: Context, uri: Uri): Bitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { input -> BitmapFactory.decodeStream(input) }
}.getOrNull()

private fun saveCroppedGuestAvatar(context: Context, source: Bitmap, rect: Rect): String? = runCatching {
    val safeRect = Rect(
        rect.left.coerceIn(0, source.width - 1),
        rect.top.coerceIn(0, source.height - 1),
        rect.right.coerceIn(1, source.width),
        rect.bottom.coerceIn(1, source.height),
    )
    val squareSize = min(safeRect.width(), safeRect.height()).coerceAtLeast(1)
    val square = Rect(safeRect.left, safeRect.top, safeRect.left + squareSize, safeRect.top + squareSize)
    val cropped = Bitmap.createBitmap(source, square.left, square.top, square.width(), square.height())
    val outputBitmap = Bitmap.createScaledBitmap(cropped, 512, 512, true)
    if (cropped !== source && !cropped.isRecycled) cropped.recycle()

    val directory = File(context.filesDir, "guest_profile").apply { mkdirs() }
    val target = File(directory, "profile_${System.currentTimeMillis()}.png")
    FileOutputStream(target).use { output ->
        check(outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to save profile photo" }
    }
    if (outputBitmap !== source && !outputBitmap.isRecycled) outputBitmap.recycle()
    target.absolutePath
}.getOrNull()
