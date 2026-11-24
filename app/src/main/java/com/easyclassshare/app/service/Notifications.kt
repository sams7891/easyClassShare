package com.easyclassshare.app.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.easyclassshare.app.MainActivity
import com.easyclassshare.app.R
import com.easyclassshare.app.data.ShareMessage

object Notifications {
    const val ONGOING_ID = 1
    private const val MESSAGE_ID = 2
    private const val CHANNEL_CLASS = "class_connection"
    private const val CHANNEL_MESSAGES = "class_messages"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CLASS,
                context.getString(R.string.channel_class),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES,
                context.getString(R.string.channel_messages),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    fun ongoing(context: Context, soundAllowed: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_CLASS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_connected_title))
            .setContentText(
                context.getString(if (soundAllowed) R.string.notif_sound_on else R.string.notif_sound_off),
            )
            .setOngoing(true)
            .setContentIntent(openApp(context))
            .build()

    fun update(context: Context, soundAllowed: Boolean) {
        if (!canNotify(context)) return
        context.getSystemService(NotificationManager::class.java)
            .notify(ONGOING_ID, ongoing(context, soundAllowed))
    }

    fun newMessage(context: Context, message: ShareMessage) {
        if (!canNotify(context)) return
        val from = message.senderName.ifBlank { context.getString(R.string.default_sender) }
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_new_message, from))
            .setContentText(message.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(MESSAGE_ID, notification)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
