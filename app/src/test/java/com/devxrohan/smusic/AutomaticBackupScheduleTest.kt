package com.devxrohan.smusic

import com.devxrohan.smusic.utils.AutomaticBackupSchedule
import org.junit.Assert.*
import org.junit.Test

class AutomaticBackupScheduleTest {
    @Test fun initialSnapshotIsDue() { assertTrue(AutomaticBackupSchedule.due(0, 1000, AutomaticBackupSchedule.LOCAL_INTERVAL)) }
    @Test fun repeatedAppOpenWithinIntervalDoesNotCreateAnotherSnapshot() {
        assertFalse(AutomaticBackupSchedule.due(1_000_000, 1_000_001, AutomaticBackupSchedule.LOCAL_INTERVAL))
    }
    @Test fun nextSnapshotIsDueAtThirtyMinutes() {
        val interval = AutomaticBackupSchedule.LOCAL_INTERVAL
        assertEquals(30 * 60_000L, interval)
        assertTrue(AutomaticBackupSchedule.due(1_000_000, 1_000_000 + interval, interval))
    }
    @Test fun clockCorrectionDoesNotPermanentlyDisableBackup() {
        assertTrue(AutomaticBackupSchedule.due(2_000_000, 1_000_000, AutomaticBackupSchedule.LOCAL_INTERVAL))
    }
}
