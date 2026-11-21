package com.devxrohan.smusic

import com.devxrohan.smusic.utils.RestoreTransaction
import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class RestoreTransactionTest {
    private fun workspace(block: (File, File, RestoreTransaction) -> Unit) {
        val root = Files.createTempDirectory("smusic-restore").toFile()
        val journal = File(root, "files/restore_transaction")
        try { block(root, journal, RestoreTransaction(root, journal)) } finally { root.deleteRecursively() }
    }
    @Test fun replaceDirectoryAndDeleteCommitTogether() = workspace { root, _, transaction ->
        val old = File(root, "files/pulse").apply { mkdirs() }; File(old, "old").writeText("old")
        val staged = File(root, "stage/new").apply { mkdirs() }; File(staged, "new").writeText("new")
        val queue = File(root, "files/queue").apply { writeText("stale") }
        transaction.apply(listOf(RestoreTransaction.Change(old, staged), RestoreTransaction.Change(queue, null)))
        assertFalse(File(old, "old").exists()); assertEquals("new", File(old, "new").readText()); assertFalse(queue.exists())
        assertEquals(RestoreTransaction.Recovery.NONE, transaction.recover())
    }
    @Test fun interruptedApplyRecoversOriginalAndRemovesNewlyCreatedTarget() = workspace { root, journal, transaction ->
        File(journal, "old").mkdirs(); File(journal, "old/0").writeText("original")
        val destination = File(root, "files/settings").apply { writeText("replacement") }
        val created = File(root, "files/new").apply { writeText("new") }
        val record = Properties().apply {
            setProperty("phase", "applying"); setProperty("count", "2")
            setProperty("0.path", "files/settings"); setProperty("0.original", "true")
            setProperty("1.path", "files/new"); setProperty("1.original", "false")
        }
        File(journal, "journal.properties").outputStream().use { record.store(it, null) }
        assertEquals(RestoreTransaction.Recovery.ROLLED_BACK, transaction.recover())
        assertEquals("original", destination.readText()); assertFalse(created.exists())
        assertEquals(RestoreTransaction.Recovery.NONE, transaction.recover())
    }
    @Test fun missingInputCannotModifyLiveData() = workspace { root, _, transaction ->
        val live = File(root, "live").apply { writeText("keep") }
        assertThrows(IllegalArgumentException::class.java) { transaction.apply(listOf(RestoreTransaction.Change(live, File(root, "missing")))) }
        assertEquals("keep", live.readText())
    }
    @Test fun targetsOutsideApplicationAreRejected() = workspace { root, _, transaction ->
        assertThrows(IllegalArgumentException::class.java) { transaction.apply(listOf(RestoreTransaction.Change(File(root, "../outside"), null))) }
    }
    @Test fun previousJournalRecoversIfInterruptedBetweenJournalRenames() = workspace { root, journal, transaction ->
        File(journal, "old").mkdirs(); File(journal, "old/0").writeText("original")
        val live = File(root, "files/settings").apply { writeText("new") }
        val record = Properties().apply { setProperty("phase", "applying"); setProperty("count", "1"); setProperty("0.path", "files/settings"); setProperty("0.original", "true") }
        File(journal, "journal.previous").outputStream().use { record.store(it, null) }
        assertEquals(RestoreTransaction.Recovery.ROLLED_BACK, transaction.recover())
        assertEquals("original", live.readText())
    }
    @Test fun durableCommitIsKeptWhenCleanupWasInterrupted() = workspace { root, journal, transaction ->
        File(journal, "old").mkdirs(); File(journal, "old/0").writeText("original")
        val live = File(root, "files/settings").apply { writeText("committed") }
        val record = Properties().apply { setProperty("phase", "committed"); setProperty("count", "1") }
        File(journal, "journal.properties").outputStream().use { record.store(it, null) }
        assertEquals(RestoreTransaction.Recovery.COMMITTED, transaction.recover())
        assertEquals("committed", live.readText()); assertFalse(journal.exists())
    }
    @Test fun missingRecoveryCopyDoesNotAcceptReplacementAsOriginal() = workspace { root, journal, transaction ->
        journal.mkdirs()
        val live = File(root, "files/settings").apply { writeText("replacement") }
        val record = Properties().apply {
            setProperty("phase", "applying"); setProperty("count", "1")
            setProperty("0.path", "files/settings"); setProperty("0.original", "true")
        }
        File(journal, "journal.properties").outputStream().use { record.store(it, null) }
        assertThrows(IllegalStateException::class.java) { transaction.recover() }
        assertEquals("replacement", live.readText())
        assertTrue(File(journal, "journal.properties").isFile)
    }
    @Test fun missingRecoveryCopyKeepsAllTargetsIntactUntilRetry() = workspace { root, journal, transaction ->
        File(journal, "old").mkdirs()
        File(journal, "old/1").writeText("second-original")
        val first = File(root, "files/first").apply { writeText("first-replacement") }
        val second = File(root, "files/second").apply { writeText("second-replacement") }
        val record = Properties().apply {
            setProperty("phase", "applying"); setProperty("count", "2")
            setProperty("0.path", "files/first"); setProperty("0.original", "true")
            setProperty("1.path", "files/second"); setProperty("1.original", "true")
        }
        File(journal, "journal.properties").outputStream().use { record.store(it, null) }
        assertThrows(IllegalStateException::class.java) { transaction.recover() }
        assertEquals("second-replacement", second.readText())
        assertEquals("second-original", File(journal, "old/1").readText())
        File(journal, "old/0").writeText("first-original")
        assertEquals(RestoreTransaction.Recovery.ROLLED_BACK, transaction.recover())
        assertEquals("first-original", first.readText())
        assertEquals("second-original", second.readText())
    }
    @Test fun rollbackCleanupCanResumeWithoutAlreadyDeletedCopies() = workspace { root, journal, transaction ->
        journal.mkdirs()
        val live = File(root, "files/settings").apply { writeText("original") }
        val record = Properties().apply { setProperty("phase", "rolled_back"); setProperty("count", "1") }
        File(journal, "journal.properties").outputStream().use { record.store(it, null) }
        assertEquals(RestoreTransaction.Recovery.ROLLED_BACK, transaction.recover())
        assertEquals("original", live.readText())
        assertFalse(journal.exists())
    }
    @Test fun applyFailureAfterFirstReplacementRollsBackEveryTarget() = workspace { root, _, transaction ->
        val first = File(root, "files/first").apply { parentFile.mkdirs(); writeText("original") }
        val replacement = File(root, "replacement").apply { writeText("replacement") }
        File(root, "blocked").writeText("parent is a file")
        assertThrows(IllegalStateException::class.java) {
            transaction.apply(listOf(RestoreTransaction.Change(first, replacement), RestoreTransaction.Change(File(root, "blocked/child"), replacement)))
        }
        assertEquals("original", first.readText())
        assertEquals("parent is a file", File(root, "blocked").readText())
        assertEquals(RestoreTransaction.Recovery.NONE, transaction.recover())
    }
    @Test fun recoveryWorkspaceAndItsParentsCannotBeRestoreTargets() = workspace { root, journal, transaction ->
        listOf(journal, journal.parentFile, File(journal, "journal.properties"), File(journal.parentFile, "restore_transaction.lock")).forEach { target ->
            assertThrows(IllegalArgumentException::class.java) { transaction.apply(listOf(RestoreTransaction.Change(target, null))) }
        }
        assertTrue(root.exists())
    }
}
