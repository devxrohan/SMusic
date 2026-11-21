package com.devxrohan.smusic.ui.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devxrohan.smusic.R
import androidx.compose.ui.res.stringResource

/**
 * 200% Master Boost Card with quick preset pills and segmented bar slider.
 */
@Composable
internal fun MasterBoostCard(
    boostPercent: Int,
    boostEnabled: Boolean,
    onBoostPercentChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                BorderStroke(
                    1.dp,
                    if (boostEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.surfaceContainer
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.ui_master_boost),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            letterSpacing = 1.6.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_200_max),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.ui_high_fidelity_hardware_digital_amplifier),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Text(
                text = "${boostPercent}%",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp,
                    color = if (boostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SegmentedBoostSlider(
            currentPercent = boostPercent,
            onPercentChange = onBoostPercentChange
        )

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(100, 125, 150, 175, 200).forEach { presetPercent ->
                val isSelected = boostPercent == presetPercent
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onBoostPercentChange(presetPercent) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${presetPercent}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    }
}

/**
 * Segmented slider bar matching design system with smooth interactive drag/tap.
 */
@Composable
private fun SegmentedBoostSlider(
    currentPercent: Int,
    onPercentChange: (Int) -> Unit
) {
    var componentWidth by remember { mutableFloatStateOf(1f) }
    val progressFraction = ((currentPercent - 100) / 100f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .onSizeChanged { componentWidth = it.width.toFloat().coerceAtLeast(1f) }
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / componentWidth).coerceIn(0f, 1f)
                    val newPercent = 100 + (frac * 100).toInt()
                    onPercentChange(newPercent)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val frac = (change.position.x / componentWidth).coerceIn(0f, 1f)
                    val newPercent = 100 + (frac * 100).toInt()
                    onPercentChange(newPercent)
                }
            }
    ) {
        // Same reasoning as above: capture theme colors in composable scope before
        // the Canvas draw lambda, which cannot call @Composable getters itself.
        val trackFillColor = MaterialTheme.colorScheme.primary
        val segmentLineColor = MaterialTheme.colorScheme.surface
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            val activeWidth = width * progressFraction

            if (activeWidth > 0) {
                drawRoundRect(
                    color = trackFillColor,
                    topLeft = Offset.Zero,
                    size = Size(activeWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2, height / 2)
                )
            }

            val segments = 10
            for (i in 1 until segments) {
                val segX = width * (i.toFloat() / segments)
                drawLine(
                    color = segmentLineColor,
                    start = Offset(segX, 0f),
                    end = Offset(segX, height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}
