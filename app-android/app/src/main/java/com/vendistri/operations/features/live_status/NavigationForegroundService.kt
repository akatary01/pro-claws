package com.vendistri.operations.features.live_status

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat

class NavigationForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        LiveStatusNotificationRenderer.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> {
                storage().edit().remove(SnapshotKey).apply()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ActionUpdate -> {
                val encoded = intent.getStringExtra(ExtraSnapshot)
                val snapshot = LiveStatusSnapshotCodec.decode(encoded) ?: restoreSnapshot()
                if (snapshot != null) publish(snapshot) else stopSelf()
            }
            else -> restoreSnapshot()?.let(::publish) ?: stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun publish(snapshot: LiveStatusSnapshot) {
        storage().edit().putString(SnapshotKey, LiveStatusSnapshotCodec.encode(snapshot)).apply()
        val notification = LiveStatusNotificationRenderer.render(this, snapshot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                LiveStatusNotificationRenderer.NotificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(LiveStatusNotificationRenderer.NotificationId, notification)
        }
    }

    private fun restoreSnapshot(): LiveStatusSnapshot? =
        LiveStatusSnapshotCodec.decode(storage().getString(SnapshotKey, null))

    private fun storage() = getSharedPreferences(StorageName, Context.MODE_PRIVATE)

    companion object {
        private const val ActionUpdate = "com.vendistri.operations.live_status.UPDATE"
        private const val ActionStop = "com.vendistri.operations.live_status.STOP"
        private const val ExtraSnapshot = "snapshot"
        private const val StorageName = "vendistri_live_status"
        private const val SnapshotKey = "active_snapshot"

        fun update(context: Context, snapshot: LiveStatusSnapshot) {
            val intent = Intent(context, NavigationForegroundService::class.java).apply {
                action = ActionUpdate
                putExtra(ExtraSnapshot, LiveStatusSnapshotCodec.encode(snapshot))
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.getSharedPreferences(StorageName, Context.MODE_PRIVATE)
                .edit()
                .remove(SnapshotKey)
                .apply()
            context.stopService(Intent(context, NavigationForegroundService::class.java))
        }
    }
}
