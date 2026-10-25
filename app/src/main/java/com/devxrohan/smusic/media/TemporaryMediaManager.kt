package com.devxrohan.smusic.media

import android.content.Context
import java.io.File
import java.util.UUID

class TemporaryMediaManager(private val context: Context) {

    private val cacheDir: File by lazy {
        File(context.cacheDir, "shared_music").apply {
            if (!exists()) mkdirs()
        }
    }

    fun createTempFile(extension: String = "wav"): File {
        val ext = if (extension.startsWith(".")) extension else ".$extension"
        return File(cacheDir, "seg_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}$ext")
    }

    fun cleanup(file: File?) {
        try {
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun cleanupAll() {
        try {
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { file ->
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }
}
