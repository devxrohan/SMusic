package com.devxrohan.smusic.share

import android.net.Uri

enum class SharedContentType {
    VIDEO,
    AUDIO,
    URL,
    TEXT,
    UNKNOWN
}

data class SharedContent(
    val type: SharedContentType,
    val uri: Uri? = null,
    val text: String? = null,
    val mimeType: String? = null,
    val sourcePackage: String? = null
)
