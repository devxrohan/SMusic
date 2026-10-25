package com.devxrohan.smusic.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Swiping a system notification only dismisses the tray copy, not the in-app unread badge. */
class AnnouncementDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Deliberately do not mark announcements as read here.
    }
}
