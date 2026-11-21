@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.devxrohan.smusic.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.constants.DisableAnimationsKey
import com.devxrohan.smusic.utils.rememberPreference

internal enum class CategoryBadgeShape { FLOWER, CLOVER, SOFT_STAR, BLOOM }

/** Shared category presentation; destinations keep their existing routes and actions. */
@Composable
internal fun CategoryCard(
    title: String,
    subtitle: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeShape: CategoryBadgeShape = CategoryBadgeShape.FLOWER,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion by rememberPreference(DisableAnimationsKey, false)
    val corners by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 34f else 26f,
        animationSpec = if (reduceMotion) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "categoryCardCorners",
    )
    val iconPolygon = remember(badgeShape) {
        when (badgeShape) {
            CategoryBadgeShape.FLOWER -> MaterialShapes.Cookie9Sided
            CategoryBadgeShape.CLOVER -> MaterialShapes.Clover4Leaf
            CategoryBadgeShape.SOFT_STAR -> MaterialShapes.Cookie6Sided
            CategoryBadgeShape.BLOOM -> MaterialShapes.Clover8Leaf
        }
    }
    val iconShape = iconPolygon.toShape()
    val contentColor = scheme.onPrimaryContainer
    val titleHeight = with(LocalDensity.current) { MaterialTheme.typography.titleMedium.lineHeight.toDp() * 2 }
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(corners.dp),
        color = scheme.primaryContainer,
        contentColor = contentColor,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(iconShape)
                        .background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.Center) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = .82f),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
