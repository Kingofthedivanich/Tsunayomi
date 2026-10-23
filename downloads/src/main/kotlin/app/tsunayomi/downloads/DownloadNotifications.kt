package app.tsunayomi.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

/** The ongoing notification of the download queue, with a button that stops it (queued chapters stay queued). */
internal object DownloadNotifications {
    private const val CHANNEL_ID = "downloads"
    private const val NOTIFICATION_ID = 7001

    fun foregroundInfo(context: Context, workId: UUID, title: String, text: String, done: Int, total: Int): ForegroundInfo {
        ensureChannel(context)
        val stop = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(total, done, total == 0)
            .addAction(0, context.getString(R.string.downloads_stop), stop)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
