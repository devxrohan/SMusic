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

/** Relaunch from the dedicated restart process after the original app process exits. */
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
        // Start the real launcher first so its SplashScreen is the only visible transition.
        // The previous implementation killed the main process before the new activity was safely
        // attached, which could leave a transient black window or trigger repeated restarts.
        if (relaunch != null) runCatching { startActivity(relaunch) }
        finish()
        overridePendingTransition(0, 0)
        if (oldPid > 0 && oldPid != Process.myPid()) {
            android.os.Handler(mainLooper).postDelayed({
                runCatching { Process.killProcess(oldPid) }
            }, 250L)
        }
    }
}
