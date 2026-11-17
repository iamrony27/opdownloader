package com.opdownloader.app.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.opdownloader.app.MainActivity
import com.opdownloader.app.R
import com.opdownloader.app.data.DownloadItem
import com.opdownloader.app.data.humanBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {
    const val CHANNEL_PROGRESS = "download_progress"
    const val CHANNEL_STATUS = "download_status"
    const val CHANNEL_SUPPORT = "dev_appreciation"
    const val PROGRESS_ID = 1001
    const val SUPPORT_ID = 2002
    private const val KEY_NUDGE_DAY = "last_nudge_day"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            "Download progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Live progress of running downloads"
            setShowBadge(false)
            enableVibration(false)
        }
        val status = NotificationChannel(
            CHANNEL_STATUS,
            "Download finished",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Completed and failed downloads" }
        val support = NotificationChannel(
            CHANNEL_SUPPORT,
            "A note from the dev",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "An occasional thank-you note and coffee fund" }
        manager.createNotificationChannel(progress)
        manager.createNotificationChannel(status)
        manager.createNotificationChannel(support)
    }

    fun progressNotification(context: Context, aggregate: Aggregate): android.app.Notification {
        val title = when (aggregate.activeCount) {
            0 -> "Preparing download"
            1 -> aggregate.title.ifBlank { "Downloading 1 file" }
            else -> "Downloading ${aggregate.activeCount} files"
        }
        val isIndeterminate = aggregate.progress <= 0f && aggregate.activeCount > 0
        val text = buildString {
            if (aggregate.speedBps > 0) {
                append(humanBytes(aggregate.speedBps)).append("/s")
            } else {
                append("Streaming…")
            }
            if (!isIndeterminate) {
                append(" • ").append((aggregate.progress * 100).toInt()).append("%")
            } else {
                append(" • Downloading actively")
            }
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, if (isIndeterminate) 0 else (aggregate.progress * 100).toInt(), isIndeterminate)
            .build()
    }

    fun notifyComplete(context: Context, item: DownloadItem) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Download complete")
            .setContentText(item.fileName)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.fileName}\n${humanBytes(item.fileSize)} saved"))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
        }
    }

    fun notifyFailed(context: Context, item: DownloadItem, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Download failed")
            .setContentText("${item.fileName}: $message")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.fileName}\n$message"))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(item.id.hashCode() + 7, notification)
        }
    }

    fun cancelProgress(context: Context, itemId: String) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(itemId.hashCode())
        }
    }

    /** Posts the thank-you note at most once per calendar day. */
    fun maybeDailyAppreciation(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val prefs = context.getSharedPreferences("op_support", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        if (prefs.getString(KEY_NUDGE_DAY, null) == today) return
        createChannels(context)
        val note = "oP Downloader is vibe-coded by one person. If it saved you time today, buy me a coffee — UPI 7074018142@ptsbi"
        val notification = NotificationCompat.Builder(context, CHANNEL_SUPPORT)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("☕ A small ask from the dev")
            .setContentText(note)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$note\n\nAny amount helps. Open Settings → Buy me a coffee to copy the UPI ID, or say hi on WhatsApp."
                )
            )
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(SUPPORT_ID, notification)
        }.onSuccess {
            prefs.edit().putString(KEY_NUDGE_DAY, today).apply()
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
