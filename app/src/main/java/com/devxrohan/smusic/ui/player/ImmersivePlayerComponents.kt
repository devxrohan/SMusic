@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.ui.player

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devxrohan.smusic.R
import com.devxrohan.smusic.ui.utils.highQualityThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun ImmersivePlayerBackdrop(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color.Black)) {
        AnimatedContent(
            targetState = thumbnailUrl,
            transitionSpec = { fadeIn(tween(540)) togetherWith fadeOut(tween(540)) },
            label = "immersiveBackdrop",
        ) { artworkUrl ->
            if (artworkUrl != null) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = artworkUrl.highQualityThumbnail(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.06f
                                scaleY = 1.06f
                                alpha = 0.82f
                            }
                    )

                    AsyncImage(
                        model = artworkUrl.highQualityThumbnail(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(90.dp)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithCache {
                                val blurMask = Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.46f to Color.Transparent,
                                        0.58f to Color.Black.copy(alpha = 0.6f),
                                        0.72f to Color.Black,
                                        1f to Color.Black,
                                    )
                                )

                                onDrawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = blurMask,
                                        blendMode = BlendMode.DstIn,
                                    )
                                }
                            }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.08f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.18f),
                            0.34f to Color.Transparent,
                            0.64f to Color.Black.copy(alpha = 0.22f),
                            1f to Color.Black.copy(alpha = 0.82f),
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.56f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surface.copy(alpha = 0.16f),
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                        )
                    )
                )
        )
    }
}

@Composable
internal fun ImmersiveBottomActions(
    textColor: Color,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onDeviceClick: () -> Unit,
    deviceName: String,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImmersiveCircleButton(
                icon = R.drawable.queue_music,
                textColor = textColor,
                onClick = onOpenQueue,
            )
            ImmersiveCircleButton(
                icon = R.drawable.lyrics,
                textColor = textColor,
                onClick = onOpenLyrics,
            )
        }

        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(textColor.copy(alpha = 0.10f))
                .clickable(onClick = onDeviceClick)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.volume_up),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun rememberPlaybackOutputName(): String {
    val context = LocalContext.current
    var outputName by remember { mutableStateOf(resolvePlaybackOutputName(context)) }

    LaunchedEffect(context) {
        while (isActive) {
            outputName = resolvePlaybackOutputName(context)
            delay(2000)
        }
    }

    return outputName
}

@Suppress("DEPRECATION")
private fun resolvePlaybackOutputName(context: Context): String {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val devices =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            } catch (_: SecurityException) {
                emptyArray()
            }
        } else {
            emptyArray()
        }

    devices.firstOrNull { it.isBluetoothOutput() }?.productName?.toString()?.takeIf {
        it.isNotBlank()
    }?.let {
        return it
    }

    val selectedRouteName =
        try {
            val mediaRouter = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
            mediaRouter
                ?.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO)
                ?.name
                ?.toString()
        } catch (_: Exception) {
            null
        }

    selectedRouteName
        ?.takeIf { it.isNotBlank() && !it.equals("Phone", ignoreCase = true) }
        ?.let { return it }

    devices.firstOrNull { it.isWiredOutput() }?.productName?.toString()?.takeIf {
        it.isNotBlank()
    }?.let {
        return it
    }

    devices.firstOrNull { it.isUsbOutput() }?.productName?.toString()?.takeIf {
        it.isNotBlank()
    }?.let {
        return it
    }

    return "Speaker"
}

private fun AudioDeviceInfo.isBluetoothOutput(): Boolean =
    type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            type == AudioDeviceInfo.TYPE_BLE_BROADCAST))

private fun AudioDeviceInfo.isWiredOutput(): Boolean =
    type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_AUX_LINE

private fun AudioDeviceInfo.isUsbOutput(): Boolean =
    type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            type == AudioDeviceInfo.TYPE_DOCK

@Composable
private fun ImmersiveCircleButton(
    icon: Int,
    textColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(textColor.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(textColor),
            modifier = Modifier.size(25.dp)
        )
    }
}
