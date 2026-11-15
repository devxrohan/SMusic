package com.devxrohan.smusic.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devxrohan.smusic.R

private data class CommunityLink(
    val label: String,
    val url: String,
    val icon: Int,
    val color: Color,
)

private val communityLinks = listOf(
    CommunityLink("Discord", "https://discord.gg/YYXdpG684e", R.drawable.ic_community_discord, Color(0xFF5865F2)),
    CommunityLink("Reddit", "https://www.reddit.com/r/SMusicApp/", R.drawable.ic_community_reddit, Color(0xFFFF4500)),
    CommunityLink("Telegram", "https://t.me/SMusicapp", R.drawable.ic_community_telegram, Color(0xFF229ED9)),
)

/** One shared community tile, styled exactly like the onboarding GitHub star tile. */
@Composable
fun CommunityLinksRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = "Follow us on",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                communityLinks.forEach { link ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            // No independent rectangular ripple: keep interaction visually quiet
                            // inside the single rounded parent tile, like the GitHub star tile.
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(link.color),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(link.icon),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = link.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/** Compact text-only social links used under App Info. */
@Composable
fun CommunityTextLinks(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        communityLinks.forEachIndexed { index, link ->
            if (index > 0) Text("  ·  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = link.label,
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(link.url) } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
