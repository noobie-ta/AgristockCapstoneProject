package com.example.agristockcapstoneproject.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.agristockcapstoneproject.R

object NotificationHelper {

    const val CHANNEL_MESSAGES = "messages_channel"
    const val CHANNEL_ALERTS = "alerts_channel"
    const val CHANNEL_BIDS = "bids_channel"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val messages = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chat messages"
                enableLights(true)
                lightColor = Color.YELLOW
            }

            val alerts = NotificationChannel(
                CHANNEL_ALERTS,
                "Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "General notifications" }

            val bids = NotificationChannel(
                CHANNEL_BIDS,
                "Bids",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Bidding updates" }

            nm.createNotificationChannel(messages)
            nm.createNotificationChannel(alerts)
            nm.createNotificationChannel(bids)
        }
    }

    fun showNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        notificationId: Int
    ) {
        ensureChannels(context)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }
}

