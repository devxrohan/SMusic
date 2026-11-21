package com.devxrohan.smusic.ui.component

/** Shared cover variant, also used by Classic playback and media notifications. */
internal fun browseArtworkUrl(url: String?): String? =
    com.devxrohan.smusic.utils.cachedArtworkUrl(url)
