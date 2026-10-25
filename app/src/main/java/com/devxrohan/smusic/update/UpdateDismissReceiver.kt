package com.devxrohan.smusic.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** System-tray swipe only dismisses that phone notification; it must not clear the in-app badge. */
class UpdateDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Deliberately no state change. The update remains unread in the app's Notifications screen.
    }
}
