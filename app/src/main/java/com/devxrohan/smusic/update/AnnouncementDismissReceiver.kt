package com.devxrohan.smusic.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnnouncementDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // A BroadcastReceiver only stays alive for the duration of
        // onReceive() — go through goAsync() so the coroutine has time to
        // actually finish the DataStore write before the receiver is torn
        // down by the system.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppNotificationManager.markAllAnnouncementsSeen(context.applicationContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
