package com.opdownloader.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.Store
import com.opdownloader.app.engine.DownloadManager
import com.opdownloader.app.engine.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        val initial = NotificationHelper.progressNotification(this, DownloadManager.aggregate.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.PROGRESS_ID,
                initial,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.PROGRESS_ID, initial)
        }
        startTicker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTicker()
        return START_NOT_STICKY
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var idleTicks = 0
            while (isActive) {
                val aggregate = DownloadManager.aggregate.value
                val active = Store.downloads.value.any {
                    it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
                }
                runCatching {
                    NotificationManagerCompat.from(this@DownloadService).notify(
                        NotificationHelper.PROGRESS_ID,
                        NotificationHelper.progressNotification(this@DownloadService, aggregate)
                    )
                }
                if (!active) {
                    idleTicks++
                    if (idleTicks >= 2) {
                        stopSelf()
                        break
                    }
                } else {
                    idleTicks = 0
                }
                delay(1000)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, DownloadService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }
    }
}
