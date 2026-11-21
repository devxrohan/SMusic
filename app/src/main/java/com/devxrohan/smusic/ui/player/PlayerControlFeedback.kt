package com.devxrohan.smusic.ui.player

import com.devxrohan.smusic.ui.component.touchHaptic

import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role

/** Keeps feedback inside the enabled click action, including accessibility activation. */
@Composable
internal fun Modifier.playerControlClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = LocalIndication.current,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val view = androidx.compose.ui.platform.LocalView.current
    return clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = indication,
        onClickLabel = onClickLabel,
        role = role,
    ) {
        view.context.touchHaptic()
        onClick()
    }
}
