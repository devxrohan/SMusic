package com.devxrohan.smusic.ui.component

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import com.devxrohan.smusic.constants.TouchHapticsEnabledKey
import com.devxrohan.smusic.constants.TouchHapticsStrengthKey
import com.devxrohan.smusic.utils.dataStore
import com.devxrohan.smusic.utils.get
import com.devxrohan.smusic.utils.rememberPreference

private var lastTickAt = 0L

/** Limits slider feedback independently of drag frequency. */
internal fun Context.touchHaptic(tick: Boolean = false) {
    if (!dataStore.get(TouchHapticsEnabledKey, true)) return
    if (Settings.System.getInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
    if (tick) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickAt < 100L) return
        lastTickAt = now
    }
    val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!vibrator.hasVibrator()) return
    val pulse = HapticPulseSpec.fromStrength(dataStore.get(TouchHapticsStrengthKey, 60), tick)
    if (pulse.durationMs == 0L) return
    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val amplitude = if (vibrator.hasAmplitudeControl()) pulse.amplitude else VibrationEffect.DEFAULT_AMPLITUDE
        vibrator.vibrate(VibrationEffect.createOneShot(pulse.durationMs, amplitude), attributes)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pulse.durationMs, attributes)
    }
}

/** Applies the app preference to Compose's long-press and selection feedback too. */
@Composable
internal fun rememberAppHaptics(): HapticFeedback {
    val enabled by rememberPreference(TouchHapticsEnabledKey, true)
    val context = LocalContext.current
    return remember(enabled, context) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (enabled) context.touchHaptic(tick = hapticFeedbackType == HapticFeedbackType.TextHandleMove)
            }
        }
    }
}
