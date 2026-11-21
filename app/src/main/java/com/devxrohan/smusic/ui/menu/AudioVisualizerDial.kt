package com.devxrohan.smusic.ui.menu

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Circular visualizer dial with dynamic White -> Green -> Glowing Red progression.
 */
@Composable
internal fun AudioVisualizerDial(
    title: String,
    captured: Boolean,
    energy: Float,
    isPeak: Boolean,
    accentDefaultColor: Color,
    modifier: Modifier = Modifier
) {
    val targetDynamicColor = when {
        isPeak || energy > 0.70f -> MaterialTheme.colorScheme.error
        energy > 0.35f -> {
            val t = ((energy - 0.35f) / 0.35f).coerceIn(0f, 1f)
            lerp(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.error, t)
        }
        energy > 0.05f -> {
            val t = ((energy - 0.05f) / 0.30f).coerceIn(0f, 1f)
            lerp(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.tertiary, t)
        }
        else -> accentDefaultColor
    }

    val animatedColor by animateColorAsState(
        targetValue = targetDynamicColor,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "dialColor"
    )

    val animatedSweep by animateFloatAsState(
        targetValue = (energy * 360f).coerceIn(0f, 360f),
        animationSpec = tween(80),
        label = "dialSweep"
    )

    Box(
        modifier = modifier
            .height(116.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(
                    1.dp,
                    if (isPeak) animatedColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer
                ),
                RoundedCornerShape(22.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Canvas's draw lambda is not a @Composable scope, so MaterialTheme.colorScheme
        // must be read here (in composable scope) first and captured into locals.
        val dialInnerColor = MaterialTheme.colorScheme.surface
        val dialTrackColor = MaterialTheme.colorScheme.surfaceContainer
        Canvas(modifier = Modifier.size(86.dp)) {
            val strokeWidth = 5.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            drawCircle(
                color = dialInnerColor,
                radius = radius - strokeWidth / 2,
                center = center
            )

            drawCircle(
                color = dialTrackColor,
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth)
            )

            if (animatedSweep > 5f) {
                drawArc(
                    color = animatedColor,
                    startAngle = -90f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )
            }

            val pillAngleRad = Math.toRadians(-90.0).toFloat()
            val pillX = center.x + radius * cos(pillAngleRad)
            val pillY = center.y + radius * sin(pillAngleRad)

            drawCircle(
                color = animatedColor,
                radius = 4.dp.toPx(),
                center = Offset(pillX, pillY)
            )

            if (isPeak) {
                drawCircle(
                    color = animatedColor.copy(alpha = 0.35f),
                    radius = 8.dp.toPx(),
                    center = Offset(pillX, pillY)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${(energy.coerceIn(0f, 1f) * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Text(
                text = if (captured) "ENERGY" else "VISUAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
