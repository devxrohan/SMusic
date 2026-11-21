package com.devxrohan.smusic.utils

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Properties

/** Startup-only restore journal. Originals stay recoverable until the commit record is durable. */
internal class RestoreTransaction(private val root: File, private val workspace: File) {
    data class Change(val target: File, val replacement: File?)
    enum class Recovery { NONE, COMMITTED, ROLLED_BACK }
    private val journal get() = File(workspace, "journal.properties")
    private val journalBackup get() = File(workspace, "journal.previous")
    private fun target(relative: String): File {
        require(relative.isNotBlank() && !File(relative).isAbsolute)
        val file = File(root, relative).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "Restore target outside app data" }
        require(file != workspace.canonicalFile && !file.path.startsWith(workspace.canonicalPath + File.separator))
        require(!workspace.canonicalPath.startsWith(file.path + File.separator)) { "Restore target contains the recovery workspace" }
        require(file != File(workspace.parentFile, "restore_transaction.lock").canonicalFile)
        return file
    }
    private fun <T> locked(block: () -> T): T {
        check(workspace.parentFile!!.exists() || workspace.parentFile!!.mkdirs())
        return RandomAccessFile(File(workspace.parentFile, "restore_transaction.lock"), "rw").channel.use { channel ->
            channel.lock().use { block() }
        }
    }
    fun recover(): Recovery = locked { recoverLocked() }
    private fun recoverLocked(): Recovery {
        if (!journal.isFile && journalBackup.isFile) check(journalBackup.renameTo(journal)) { "Could not recover restore journal" }
        if (!journal.isFile) { if (workspace.exists()) check(workspace.deleteRecursively()); return Recovery.NONE }
        val record = Properties().apply { journal.inputStream().use { load(it) } }
        val count = record.getProperty("count").toInt().also { require(it in 0..256) }
        val phase = record.getProperty("phase")
        if (phase == "committed" || phase == "rolled_back") {
            check(workspace.deleteRecursively())
            return if (phase == "committed") Recovery.COMMITTED else Recovery.ROLLED_BACK
        }
        require(phase == "applying") { "Unsupported restore recovery phase" }
        val destinations = (0 until count).map { index ->
            val path = requireNotNull(record.getProperty("$index.path")) { "Missing restore target" }
            val original = requireNotNull(record.getProperty("$index.original")) { "Missing restore target state" }.toBooleanStrict()
            Triple(target(path), original, File(workspace, "old/$index"))
        }
        validateTargets(destinations.map { it.first })
        // Validate the complete journal before touching any live data. A missing copy
        // must not leave half the settings rolled back and the database still replaced.
        destinations.filter { it.second }.forEach { check(it.third.exists()) { "Missing restore recovery copy" } }
        // Reverse order also supports database companion files and directory replacements.
        for ((destination, original, old) in destinations.asReversed()) {
            if (original) {
                remove(destination)
                // Retain every original until ALL targets have recovered. If copying is
                // interrupted, the next startup can safely retry even a partially written file.
                copy(old, destination)
            } else remove(destination)
        }
        // If cleanup is interrupted, the next startup must not require originals
        // which cleanup may already have deleted after a successful rollback.
        record.setProperty("phase", "rolled_back")
        writeJournal(record)
        check(workspace.deleteRecursively())
        return Recovery.ROLLED_BACK
    }
    fun apply(changes: List<Change>) = locked {
        recoverLocked()
        require(changes.size <= 256)
        val paths = changes.map { it.target.canonicalFile.relativeTo(root.canonicalFile).path }
        validateTargets(paths.map(::target))
        check(workspace.mkdirs())
        val record = Properties().apply { setProperty("count", changes.size.toString()); setProperty("phase", "applying") }
        try {
            changes.forEachIndexed { index, change ->
                val destination = target(paths[index])
                record.setProperty("$index.path", paths[index])
                record.setProperty("$index.original", destination.exists().toString())
                if (destination.exists()) copy(destination, File(workspace, "old/$index"))
                change.replacement?.let { copy(it, File(workspace, "new/$index")) }
            }
            writeJournal(record) // No live path changes before ALL recovery copies exist.
            changes.forEachIndexed { index, change ->
                val destination = target(paths[index])
                remove(destination)
                if (change.replacement != null) {
                    check(destination.parentFile!!.exists() || destination.parentFile!!.mkdirs())
                    check(File(workspace, "new/$index").renameTo(destination)) { "Could not install ${destination.name}" }
                }
            }
            record.setProperty("phase", "committed")
            writeJournal(record)
        } catch (failure: Exception) {
            if (journal.isFile || journalBackup.isFile) {
                try { recoverLocked() } catch (recovery: Exception) { failure.addSuppressed(recovery) }
            } else workspace.deleteRecursively()
            throw failure
        }
        // Cleanup is retryable at next startup; it must not turn a durable commit into a rollback.
        workspace.deleteRecursively()
    }
    private fun validateTargets(files: List<File>) {
        val paths = files.map { it.canonicalPath }
        require(paths.distinct().size == paths.size) { "Duplicate restore targets" }
        require(paths.none { a -> paths.any { b -> a != b && b.startsWith(a + File.separator) } }) { "Overlapping restore targets" }
    }
    private fun writeJournal(record: Properties) {
        val temporary = File(workspace, "journal.tmp")
        FileOutputStream(temporary).use { stream -> record.store(stream, null); stream.fd.sync() }
        // Rotate the previous durable record before promotion. Recovery also handles the gap
        // between these renames; this works on filesystems that cannot rename over a file.
        if (journal.exists()) {
            if (journalBackup.exists()) check(journalBackup.delete())
            check(journal.renameTo(journalBackup)) { "Could not preserve restore journal" }
        }
        if (!temporary.renameTo(journal)) {
            if (journalBackup.exists()) journalBackup.renameTo(journal)
            error("Could not persist restore journal")
        }
        journalBackup.delete()
    }
    private fun remove(file: File) { if (file.exists()) check(file.deleteRecursively()) { "Could not replace ${file.name}" } }
    private fun copy(source: File, destination: File) {
        require(source.exists()) { "Restore input missing" }
        if (source.isDirectory) {
            check(destination.mkdirs())
            source.listFiles()?.forEach { copy(it, File(destination, it.name)) } ?: error("Could not read restore directory")
        } else {
            check(destination.parentFile!!.exists() || destination.parentFile!!.mkdirs())
            source.inputStream().use { input -> FileOutputStream(destination).use { output -> input.copyTo(output); output.fd.sync() } }
        }
    }
}
