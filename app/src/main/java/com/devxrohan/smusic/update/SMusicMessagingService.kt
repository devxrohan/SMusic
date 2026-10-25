package com.devxrohan.smusic.update

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/**
 * Receives Firebase push messages.
 *
 * Send DATA-only messages: {id, title, message, expiresAt, imageUrl}. expiresAt = epoch millis, 0 = no limit. The app then decides what to do:
 * the message is saved for the notification bell and shown as a notification, even if the app is closed.
 * (A message that also carries a "notification" block is shown by the system itself while the app is in
 * the background; it is saved to the bell when the user taps it.)
 */
class SMusicMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title.orEmpty()
        val body = data["message"] ?: data["body"] ?: message.notification?.body.orEmpty()
        val id = data["id"]?.takeIf { it.isNotBlank() } ?: message.messageId ?: System.currentTimeMillis().toString()
        // We are already on Firebase's worker thread (about 20 s allowed), so block until it is saved.
        runBlocking {
            runCatching {
                AppNotificationManager.createNotificationChannels(applicationContext)
                AppNotificationManager.onPushAnnouncement(
                    applicationContext,
                    AnnouncementInfo(
                        id = id,
                        title = title,
                        message = body,
                        enabled = true,
                        expiresAt = data["expiresAt"]?.toLongOrNull() ?: 0L,
                        imageUrl = data["imageUrl"].orEmpty(),
                    ),
                )
            }
        }
    }

    override fun onNewToken(token: String) {
        // Topic subscriptions survive token refreshes, but re-subscribing is cheap and keeps it certain.
        runCatching { FirebaseMessaging.getInstance().subscribeToTopic(PUSH_TOPIC) }
    }

    companion object {
        /** Everyone is subscribed to this topic; send to it to reach all users. */
        const val PUSH_TOPIC = "all"
    }
}
