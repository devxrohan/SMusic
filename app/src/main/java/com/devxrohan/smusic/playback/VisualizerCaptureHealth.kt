package com.devxrohan.smusic.playback

/** Missing native frames trigger decoration; genuine silence still counts as a captured frame. */
internal fun visualizerCaptureIsStale(lastFrameAt: Long?, now: Long): Boolean =
    lastFrameAt == null || now - lastFrameAt >= 1_500L
