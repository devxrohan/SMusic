package com.devxrohan.smusic.update

import android.content.Context
import java.io.File

/**
 * Everything about the APKs the in-app updater downloads, in one place, so the
 * downloader, the "clear" button in Storage settings and the automatic
 * cleanup all agree on where the files are.
 *
 * Files live in the app cache (cacheDir/updates). The legacy external
 * "updates" folder from older builds is cleaned too.
 */
object UpdateFiles {
    fun dir(context: Context): File = File(context.cacheDir, "updates").apply { mkdirs() }

    private fun allFiles(context: Context): List<File> {
        val internal = File(context.cacheDir, "updates").listFiles()?.toList().orEmpty()
        val legacy = runCatching { context.getExternalFilesDir("updates")?.listFiles()?.toList() }.getOrNull().orEmpty()
        return internal + legacy
    }

    /** Total bytes used by downloaded update files. */
    fun size(context: Context): Long = allFiles(context).sumOf { it.length() }

    /** Deletes every downloaded update file. Returns the bytes freed. */
    fun clear(context: Context): Long {
        var freed = 0L
        allFiles(context).forEach { file ->
            val length = file.length()
            if (runCatching { file.delete() }.getOrDefault(false)) freed += length
        }
        return freed
    }

    /** Deletes downloaded APKs that are not newer than the installed version (i.e. already installed). */
    fun deleteInstalledOrOutdated(context: Context, currentVersionName: String): Long {
        var freed = 0L
        allFiles(context).forEach { file ->
            val name = file.name
            val version = name.removePrefix("smusic-").removeSuffix(".apk")
            val stale = !name.startsWith("smusic-") || !name.endsWith(".apk") ||
                !VersionCompare.isNewer(version, currentVersionName)
            if (stale) {
                val length = file.length()
                if (runCatching { file.delete() }.getOrDefault(false)) freed += length
            }
        }
        return freed
    }
}
