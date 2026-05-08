package dev.truebackup.app.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.truebackup.app.MainActivity

/**
 * Foreground service that keeps backup/restore operations alive when
 * the app is backgrounded. Started by AppListViewModel / RestoreViewModel.
 */
class BackupForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "truebackup_ops"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra("label") ?: "Running backup operation…"
        startForeground(NOTIFICATION_ID, buildNotification(label))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(label: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TrueBackup")
            .setContentText(label)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Backup Operations", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Shows when a backup or restore is in progress"
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
