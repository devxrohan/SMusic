package com.devxrohan.smusic.models

import android.net.Uri

data class LocalSong(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // milliseconds
    val uri: Uri,
    val albumArtUri: Uri?,
    val size: Long,
    val dateAdded: Long,
    /** Full folder path, e.g. "Music/Downloads" (relative on Android 10+, absolute before). Empty if unknown. */
    val folder: String = "",
) {
    val durationText: String
        get() {
            val totalSeconds = duration / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
        }

    /** Last segment of [folder], for display. */
    val folderName: String
        get() = folder.trimEnd('/').substringAfterLast('/').ifBlank { "Internal storage" }
}
