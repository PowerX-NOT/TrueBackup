package dev.truebackup.app.root

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.truebackup.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app process and its link to the libsu root daemon alive after setup so backup/restore
 * after swipe-away does not bootstrap a new `su` in the UI process (Magisk toast).
 */
class RootSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch {
            runCatching { RootShellClient.execute("true") }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.root_session_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.root_session_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.root_session_notification_title))
            .setContentText(getString(R.string.root_session_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    companion object {
        private const val CHANNEL_ID = "root_session"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) {
            val intent = Intent(context, RootSessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RootSessionService::class.java))
        }
    }
}
