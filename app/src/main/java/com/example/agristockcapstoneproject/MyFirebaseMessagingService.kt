package com.example.agristockcapstoneproject

import android.util.Log
import com.example.agristockcapstoneproject.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        saveToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val type = message.data["type"] ?: "alert"
        val title = message.data["title"] ?: message.notification?.title ?: "Agristock"
        val body = message.data["body"] ?: message.notification?.body ?: ""

        val channel = when (type) {
            "message" -> NotificationHelper.CHANNEL_MESSAGES
            "bid" -> NotificationHelper.CHANNEL_BIDS
            else -> NotificationHelper.CHANNEL_ALERTS
        }

        NotificationHelper.showNotification(
            this,
            channel,
            title,
            body,
            System.currentTimeMillis().toInt()
        )
    }

    private fun saveToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.w("FCM", "Cannot save token: User not logged in")
            return
        }
        
        val db = FirebaseFirestore.getInstance()
        val tokenData = hashMapOf(
            "fcmToken" to token,
            "fcmTokenUpdatedAt" to com.google.firebase.Timestamp.now()
        )
        
        // Use set with merge to handle cases where user document might not exist
        db.collection("users").document(uid)
            .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                Log.d("FCM", "FCM token saved successfully for user: $uid")
            }
            .addOnFailureListener { exception ->
                Log.e("FCM", "Failed to save FCM token: ${exception.message}")
            }
    }
}

