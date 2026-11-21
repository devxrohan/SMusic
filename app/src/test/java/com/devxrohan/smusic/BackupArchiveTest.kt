package com.devxrohan.smusic

import com.devxrohan.smusic.utils.BackupArchive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class BackupArchiveTest {
    private fun archive(name: String): ByteArray = ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write("settings".toByteArray())
            zip.closeEntry()
        }
    }.toByteArray()

    private fun extract(bytes: ByteArray, allowed: (String) -> Boolean = { true }) {
        val root = Files.createTempDirectory("backup-test").toFile()
        try {
            val staging = root.resolve("staging").apply { mkdirs() }
            BackupArchive.extract(ByteArrayInputStream(bytes), staging, allowed)
            assertEquals("settings", staging.walkTopDown().first { it.isFile }.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun validArchivePreservesContents() = extract(archive("datastore/settings.preferences_pb"))

    @Test fun truncatedArchiveIsRejectedEvenWhenFilePayloadIsComplete() {
        val bytes = archive("song.db")
        assertThrows(Exception::class.java) { extract(bytes.copyOf(bytes.size - 22)) }
    }

    @Test fun unsafePathsAreRejected() {
        listOf("../song.db", "/song.db", "files/../../song.db", "files\\song.db", "C:/song.db").forEach {
            assertThrows(Exception::class.java) { extract(archive(it)) }
        }
    }

    @Test fun untrustedMarkerCannotBeRestored() {
        assertThrows(Exception::class.java) { extract(archive(".ready")) { it == "song.db" } }
    }
    @Test fun repeatedSeparatorsCannotAliasAnExistingFile() {
        listOf("files//pulse/state.json", "files/pulse//state.json", "datastore//settings.preferences_pb").forEach {
            assertThrows(IllegalArgumentException::class.java) { extract(archive(it)) }
        }
    }
    @Test fun fileAndDirectoryCannotClaimTheSameRestorePath() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("files/pulse")); zip.write("settings".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("files/pulse/")); zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) { extract(bytes.toByteArray()) }
    }
    @Test fun explicitDirectoryEntriesRemainCompatible() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("datastore/")); zip.closeEntry()
            zip.putNextEntry(ZipEntry("datastore/settings.preferences_pb")); zip.write("settings".toByteArray()); zip.closeEntry()
        }
        extract(bytes.toByteArray())
    }
}
