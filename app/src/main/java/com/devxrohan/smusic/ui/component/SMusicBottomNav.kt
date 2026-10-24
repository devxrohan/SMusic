package com.devxrohan.smusic.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.constants.NavBarStyle

/**
 * The actual bug fixed here: this was referenced everywhere (this file,
 * MainActivity's item-mapping) but never had a real definition anywhere
 * in the project — a genuine "used but never declared" gap.
 */
data class CurvedBottomNavigationItem(
    val iconActive: Int,
    val iconInactive: Int,
    val titleId: Int,
    val title: String = ""
)

/**
 * SMusic-style bottom nav — pill-shaped container with soft primary-tinted
 * pill behind the selected item. Selected = filled icon + label + tinted bg.
 * Unselected = outlined icon, muted color, no label. Floats above content on
 * an elevated rounded-rectangle surface so it visually separates from scroll.
 */
@Composable
fun SMusicBottomNav(
    items: List<CurvedBottomNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    showLabels: Boolean = true,
    style: NavBarStyle = NavBarStyle.FLOATING
) {
    // The incoming modifier may occupy the full bottom-navigation host area.
    // Keep this positioning layer non-clickable: a clickable fillMaxSize Box
    // intercepts every touch on every screen, blocking scrolling and controls.
    // Touches are consumed only by the actual visible nav surface below.
    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter
    ) {
        if (style == NavBarStyle.FLOATING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 56.dp)
                    .scale(scale)
                    .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp), clip = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    SMusicNavPill(
                        item = item,
                        selected = selected,
                        onClick = { onItemSelected(index) },
                        showLabel = showLabels
                    )
                }
            }
        } else {
            val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp + systemNavInset)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .scale(scale)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        SMusicNavPill(
                            item = item,
                            selected = selected,
                            onClick = { onItemSelected(index) },
                            showLabel = showLabels
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SMusicNavPill(
    item: CurvedBottomNavigationItem,
    selected: Boolean,
    onClick: () -> Unit,
    showLabel: Boolean
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                      else Color.Transparent,
        animationSpec = tween(200),
        label = "navPillBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(200),
        label = "navPillContent"
    )
    val iconRes = if (selected) item.iconActive else item.iconInactive
    val label = if (item.title.isNotEmpty()) item.title
                else androidx.compose.ui.res.stringResource(item.titleId)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        if (selected && showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
