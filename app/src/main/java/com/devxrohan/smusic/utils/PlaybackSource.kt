package com.devxrohan.smusic.utils

internal object PlaybackSource {
    fun isLocal(id: String?, stream: String? = null): Boolean =
        listOfNotNull(id, stream).any { value ->
            value.startsWith("local:", true) || value.startsWith("content://", true) || value.startsWith("file://", true)
        }
}
