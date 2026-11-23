package com.devxrohan.smusic.utils

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipFile

/** Validate a complete archive before the caller marks a restore ready. No live files are touched. */
internal object BackupArchive {
    private val applicationIds = setOf("com.devxrohan.smusic", "com.devxrohan.smusic.debug")

    /** Release and debug builds share the same backup format and database schema. */
    fun isCompatibleApplication(source: String, target: String): Boolean =
        source in applicationIds && target in applicationIds

    private const val MAX_BYTES = 1024L * 1024 * 1024
    private const val MAX_ENTRIES = 50_000

    fun extract(input: InputStream, staging: File, allowed: (String) -> Boolean) {
        val archive = File.createTempFile("restore-", ".zip", staging.parentFile)
        try {
            input.use { source -> archive.outputStream().use { copyLimited(source, it, MAX_BYTES) } }
            // ZipFile requires the central directory: a truncated stream must not become a valid restore.
            ZipFile(archive).use { zip ->
                val seen = HashSet<String>()
                var total = 0L
                val entries = zip.entries()
                var count = 0
                while (entries.hasMoreElements()) {
                    check(++count <= MAX_ENTRIES) { "Backup contains too many entries" }
                    val entry = entries.nextElement()
                    val name = entry.name
                    val path = if (entry.isDirectory) name.removeSuffix("/") else name
                    require(!name.startsWith('/') && !name.contains('\\') && !name.contains(':') &&
                        path.isNotBlank() && path.split('/').none { it.isEmpty() || it == ".." || it == "." || '\u0000' in it }) { "Invalid backup path" }
                    require(seen.add(path)) { "Duplicate backup entry" }
                    if (entry.isDirectory) continue
                    // Exclude control files and arbitrary files/ or shared_prefs/ payloads.
                    require(allowed(name)) { "Unsupported backup entry: $name" }
                    val target = File(staging, name)
                    require(target.canonicalPath.startsWith(staging.canonicalPath + File.separator))
                    check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                    val crc = CRC32()
                    val bytes = zip.getInputStream(entry).use { source ->
                        target.outputStream().use { output -> copyLimited(source, output, MAX_BYTES - total, crc) }
                    }
                    require(bytes == entry.size && crc.value == entry.crc) { "Corrupt backup entry" }
                    total += bytes
                }
            }
        } finally {
            archive.delete()
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, limit: Long, crc: CRC32? = null): Long {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return total
            total += count
            require(total <= limit) { "Backup exceeds the 1 GiB restore limit" }
            output.write(buffer, 0, count)
            crc?.update(buffer, 0, count)
        }
    }
}
