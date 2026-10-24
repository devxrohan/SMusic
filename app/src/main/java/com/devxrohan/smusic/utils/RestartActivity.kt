/*
 * SMusic Project Original (2026)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.devxrohan.smusic.utils

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process

/**
 * Lives in its own process (":restart"). The main process starts this activity and then exits; this activity
 * relaunches the real launcher activity, so the new main process is created cleanly after the old one is gone.
 * Used by [AppBackup.restartApp] so that restoring a backup brings the app straight back up.
 */
class RestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val relaunch: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(AppBackup.EXTRA_RELAUNCH_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(AppBackup.EXTRA_RELAUNCH_INTENT)
        }
        val oldPid = intent.getIntExtra(AppBackup.EXTRA_OLD_PID, -1)
        if (oldPid > 0 && oldPid != Process.myPid()) runCatching { Process.killProcess(oldPid) }
        if (relaunch != null) runCatching { startActivity(relaunch) }
        finish()
        overridePendingTransition(0, 0)
        Runtime.getRuntime().exit(0)
    }
}
