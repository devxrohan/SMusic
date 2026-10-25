package com.devxrohan.smusic.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Fired when the user swipes away a push message notification. */
class PushDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id").orEmpty()
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AppNotificationManager.onPushNotificationSwiped(context.applicationContext, id)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
