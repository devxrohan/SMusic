@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.devxrohan.smusic.playback

import android.content.Context
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor

/** Each decoder owns its processing buffers; active effects are copied for the outgoing tail. */
internal class CrossfadeRenderersFactory(context: Context, dj: DjAudioProcessor,
    spatial: SpatialAudioProcessor, orbit: EightDAudioProcessor) : DefaultRenderersFactory(context) {
    private val appContext = context.applicationContext
    val tailDj = DjAudioProcessor().apply {
        echoEnabled = dj.echoEnabled; echoDelayMs = dj.echoDelayMs; echoFeedback = dj.echoFeedback
        echoWetMix = dj.echoWetMix; echoPingPong = dj.echoPingPong; filterSweep = dj.filterSweep
        flangerEnabled = dj.flangerEnabled; flangerRate = dj.flangerRate; flangerDepth = dj.flangerDepth
        saturation = dj.saturation; bassCutHz = dj.bassCutHz; wowDepth = dj.wowDepth
        vinylNoiseLevel = dj.vinylNoiseLevel; pumpDepth = dj.pumpDepth; bassShelfGain = dj.bassShelfGain
        enabled = dj.enabled; bypass = dj.bypass
    }
    val tailSpatial = SpatialAudioProcessor().apply { enabled = spatial.enabled; bypass = spatial.bypass }
    val tailOrbit = EightDAudioProcessor().apply { enabled = orbit.enabled; bypass = orbit.bypass; level = orbit.level }
    override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean) =
        DefaultAudioSink.Builder(appContext).setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(
                arrayOf(tailDj, tailSpatial, tailOrbit), SilenceSkippingAudioProcessor(2_000_000, 20_000, 256), SonicAudioProcessor()))
            .build()
}
