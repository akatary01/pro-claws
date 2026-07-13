package com.vendistri.operations.features.live_status

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vendistri.operations.MainActivity
import com.vendistri.operations.R

internal object LiveStatusNotificationRenderer {
    const val ChannelId = "vendistri_active_work"
    const val NotificationId = 4107

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ChannelId,
                "Active navigation and work",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live navigation, arrival, and active task progress"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun render(context: Context, snapshot: LiveStatusSnapshot): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ActionOpenActiveWork
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.ExtraStopId, snapshot.stopId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val expandedText = listOfNotNull(
            snapshot.primaryStatus,
            snapshot.secondaryStatus,
            snapshot.address
        ).joinToString("\n")
        val builder = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_notification_v)
            .setLargeIcon(vectorBitmap(context, R.drawable.ic_live_status_v, 48))
            .setColor(ContextCompat.getColor(context, R.color.vend_blue))
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.primaryStatus)
            .setSubText(snapshot.destination)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setShowWhen(false)

        val current = snapshot.progressCurrent
        val total = snapshot.progressTotal
        if (current != null && total != null && total > 0) {
            builder.setProgress(total, current.coerceIn(0, total), false)
        } else if (snapshot.isRerouting) {
            builder.setProgress(0, 0, true)
        }

        // Android 16+ can promote this otherwise-standard ongoing notification as a Live Update.
        if (Build.VERSION.SDK_INT >= 36) {
            builder.extras.putBoolean("android.requestPromotedOngoing", true)
        }
        return builder.build()
    }

    private fun vectorBitmap(context: Context, resourceId: Int, sizeDp: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, resourceId) ?: return null
        val density = context.resources.displayMetrics.density
        val pixels = (sizeDp * density).toInt().coerceAtLeast(1)
        return drawable.toBitmap(pixels, pixels)
    }

    private fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        return bitmap
    }
}
