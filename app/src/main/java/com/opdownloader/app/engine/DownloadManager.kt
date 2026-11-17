package com.opdownloader.app.engine

import android.content.Context
import com.opdownloader.app.data.DownloadCategory
import com.opdownloader.app.data.DownloadItem
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.Store
import com.opdownloader.app.data.mediaMimeFor
import com.opdownloader.app.data.partFile
import com.opdownloader.app.data.workFile
import com.opdownloader.app.service.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

data class Aggregate(
    val activeCount: Int = 0,
    val speedBps: Long = 0,
    val progress: Float = 0f,
    val title: String = "",
)

/**
 * Owns the download queue: parses items, respects the concurrency limit, publishes
 * finished files into the public MediaStore, and keeps the foreground service alive.
 */
object DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val retries = ConcurrentHashMap<String, Int>()
    private val pumpLock = Mutex()

    private val _aggregate = MutableStateFlow(Aggregate())
    val aggregate: StateFlow<Aggregate> = _aggregate.asStateFlow()

    private lateinit var appContext: Context
    @Volatile private var recovered = false

    fun init(context: Context) {
        appContext = context.applicationContext
        AppContextHolder.context = appContext
        NotificationHelper.createChannels(appContext)
        if (!recovered) {
            recovered = true
            // Jobs never survive a process restart. Anything left as QUEUED/DOWNLOADING
            // is stale, so surface it as paused (with partial files kept for resume).
            Store.downloads.value
                .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
                .forEach { item ->
                    Store.update(item.id) {
                        it.copy(
                            status = DownloadStatus.PAUSED,
                            speedBps = 0,
                            etaSeconds = 0,
                            error = "Interrupted by app restart - tap resume to continue",
                        )
                    }
                }
        }
    }

    fun enqueue(item: DownloadItem) {
        Store.add(item)
        ensureService()
        pump()
    }

    fun start(id: String) {
        val item = Store.get(id) ?: return
        if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
            MediaTools.clearCancelled(id)
            Store.update(id) {
                it.copy(status = DownloadStatus.QUEUED, error = null, speedBps = 0, etaSeconds = 0)
            }
        }
        ensureService()
        pump()
    }

    fun pause(id: String) {
        val item = Store.get(id) ?: return
        if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
            if (item.ytDlp) MediaTools.cancel(id)
            Store.update(id) {
                it.copy(status = DownloadStatus.PAUSED, speedBps = 0, etaSeconds = 0)
            }
            jobs[id]?.cancel()
            jobs.remove(id)
            emitAggregate()
        }
    }

    fun pauseAll() {
        Store.downloads.value
            .filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
            .forEach { pause(it.id) }
    }

    fun resumeAll() {
        Store.downloads.value
            .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
            .forEach { start(it.id) }
    }

    fun retry(id: String) = start(id)

    fun remove(id: String, deleteFiles: Boolean) {
        val item = Store.get(id) ?: return
        if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
            if (item.ytDlp) MediaTools.cancel(id)
            jobs[id]?.cancel()
            jobs.remove(id)
        }
        if (deleteFiles && item.status == DownloadStatus.COMPLETED) {
            FilePublisher.deleteFile(appContext, item.fileUri, item.publicPath)
        }
        runCatching { partFile(appContext, id).delete() }
        runCatching { workFile(appContext, id).delete() }
        runCatching { File(appContext.cacheDir, "ytdlp_$id").deleteRecursively() }
        Store.remove(id)
        emitAggregate()
        NotificationHelper.cancelProgress(appContext, id)
    }

    fun deleteCompletedFiles(items: List<DownloadItem>) {
        items.forEach {
            FilePublisher.deleteFile(appContext, it.fileUri, it.publicPath)
            Store.remove(it.id)
        }
        emitAggregate()
    }

    private fun pump() {
        scope.launch {
            pumpLock.withLock {
                val settings = Store.settings.value
                while (true) {
                    val all = Store.downloads.value
                    val running = all.count { it.status == DownloadStatus.DOWNLOADING }
                    if (running >= settings.maxConcurrent) break
                    val next = all.firstOrNull { it.status == DownloadStatus.QUEUED } ?: break
                    if (settings.wifiOnly && !NetworkState.isWifi(appContext)) {
                        Store.update(next.id) {
                            it.copy(
                                status = DownloadStatus.PAUSED,
                                error = "Waiting for Wi-Fi (Wi-Fi only is enabled)"
                            )
                        }
                        continue
                    }
                    launchDownload(next.id)
                }
                emitAggregate()
            }
            if (jobs.isEmpty() && Store.downloads.value.none { it.status == DownloadStatus.QUEUED }) {
                DownloadService.stop(appContext)
            }
        }
    }

    private fun launchDownload(id: String) {
        if (jobs.containsKey(id)) return
        val job = scope.launch {
            val initial = Store.get(id) ?: return@launch
            Store.update(id) {
                it.copy(
                    status = DownloadStatus.DOWNLOADING,
                    speedBps = 0,
                    etaSeconds = 0,
                    error = null,
                    percent = if (it.fileSize > 0) it.percent else 0f,
                )
            }
            emitAggregate()
            try {
                if (initial.ytDlp) {
                    runYtDlp(initial)
                } else {
                    runDirect(initial)
                }
            } catch (_: CancellationException) {
                // paused by the user; ensure status remains PAUSED
                Store.update(id) {
                    if (it.status == DownloadStatus.DOWNLOADING) it.copy(status = DownloadStatus.PAUSED, speedBps = 0, etaSeconds = 0) else it
                }
            } catch (e: Exception) {
                val item = Store.get(id) ?: return@launch
                if (MediaTools.isCancelled(id) || item.status == DownloadStatus.PAUSED) {
                    // The user paused this transfer. Keep the paused state and partial files.
                    return@launch
                }
                val attempt = (retries[id] ?: 0) + 1
                val settings = Store.settings.value
                if (settings.autoRetry && attempt <= 2) {
                    retries[id] = attempt
                    Store.update(id) {
                        it.copy(status = DownloadStatus.QUEUED, speedBps = 0, etaSeconds = 0)
                    }
                    kotlinx.coroutines.delay(1500L * attempt)
                } else {
                    val message = MediaTools.shortError(e)
                    Store.update(id) {
                        it.copy(
                            status = DownloadStatus.FAILED,
                            speedBps = 0,
                            etaSeconds = 0,
                            percent = -1f,
                            error = message,
                        )
                    }
                    NotificationHelper.notifyFailed(appContext, item, message)
                }
            } finally {
                jobs.remove(id)
                emitAggregate()
                pump()
            }
        }
        jobs[id] = job
        ensureService()
    }

    private suspend fun runDirect(item: DownloadItem) {
        val file = DirectDownloader.download(item) { update ->
            Store.update(item.id) {
                it.copy(
                    downloadedBytes = update.downloadedBytes,
                    fileSize = max(it.fileSize, update.totalBytes),
                    speedBps = update.speedBps,
                    etaSeconds = update.etaSeconds,
                    percent = if (update.totalBytes > 0)
                        (update.downloadedBytes.toFloat() / update.totalBytes).coerceIn(0f, 1f)
                    else -1f,
                )
            }
            emitAggregate()
        }
        publish(item, file, item.fileName)
    }

    private suspend fun runYtDlp(item: DownloadItem) {
        var lastPercent = 0f
        var lastTime = System.currentTimeMillis()
        var lastUiTime = 0L
        var lastSpeed = 0L
        val media = MediaTools.download(appContext, item) { update ->
            if (MediaTools.isCancelled(item.id)) return@download
            val current = Store.get(item.id)
            if (current == null || current.status != DownloadStatus.DOWNLOADING) return@download

            val now = System.currentTimeMillis()
            val elapsed = now - lastTime
            val p = if (update.percent >= 0f) update.percent.coerceIn(0f, 1f) else -1f
            val total = if (update.totalBytes > 0) update.totalBytes else item.fileSize
            val speed = when {
                update.speedBps > 0 -> update.speedBps.also { lastSpeed = it }
                elapsed > 250 && p > lastPercent && total > 0 ->
                    ((p - lastPercent) * total * 1000L / elapsed).toLong()
                else -> lastSpeed
            }
            if (elapsed > 400) {
                lastPercent = p
                lastTime = now
            }
            // yt-dlp can emit many progress lines per second; throttle the store
            // writes so the UI stays smooth but always gets the final sample.
            if (now - lastUiTime < 150 && p in 0f..0.999f) return@download
            lastUiTime = now
            Store.update(item.id) {
                if (it.status != DownloadStatus.DOWNLOADING) it
                else it.copy(
                    percent = p,
                    fileSize = if (total > 0) total else it.fileSize,
                    downloadedBytes = update.downloadedBytes,
                    speedBps = speed,
                    etaSeconds = update.etaSeconds,
                    status = DownloadStatus.DOWNLOADING,
                )
            }
            emitAggregate()
        }
        val mime = mediaMimeFor(media.displayName, if (item.audioOnly) "audio/mpeg" else "video/mp4")
        val category = if (item.audioOnly) DownloadCategory.AUDIO else DownloadCategory.VIDEO
        publish(item.copy(fileName = media.displayName, mimeType = mime, category = category), media.file, media.displayName)
    }

    private fun publish(item: DownloadItem, file: File, displayName: String) {
        val size = file.length()
        val mime = item.mimeType.ifBlank { mediaMimeFor(displayName) }
        val category = if (item.category == DownloadCategory.OTHER) DownloadCategory.fromFileName(displayName) else item.category
        val published = FilePublisher.publish(appContext, file, displayName, mime, category)
        if (published == null) {
            Store.update(item.id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    error = "Could not save the finished file to storage",
                    speedBps = 0,
                    etaSeconds = 0,
                )
            }
            NotificationHelper.notifyFailed(appContext, item, "Could not save the file")
            return
        }
        Store.update(item.id) {
            it.copy(
                fileName = displayName,
                mimeType = mime,
                category = category,
                status = DownloadStatus.COMPLETED,
                fileSize = size,
                downloadedBytes = size,
                percent = 1f,
                speedBps = 0,
                etaSeconds = 0,
                completedAt = System.currentTimeMillis(),
                fileUri = published.uri.toString(),
                publicPath = published.displayPath,
                error = null,
            )
        }
        retries.remove(item.id)
        runCatching { partFile(appContext, item.id).delete() }
        if (item.ytDlp) {
            runCatching { File(appContext.cacheDir, "ytdlp_${item.id}").deleteRecursively() }
        }
        NotificationHelper.notifyComplete(appContext, Store.get(item.id) ?: item)
        emitAggregate()
    }

    private fun emitAggregate() {
        val all = Store.downloads.value
        val active = all.filter { it.status == DownloadStatus.DOWNLOADING }
        val totalBytes = active.sumOf { if (it.fileSize > 0) it.fileSize else 0L }
        val doneBytes = active.sumOf { it.downloadedBytes }
        _aggregate.value = Aggregate(
            activeCount = active.size,
            speedBps = active.sumOf { it.speedBps },
            progress = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f,
            title = active.firstOrNull()?.fileName.orEmpty(),
        )
    }

    private fun ensureService() {
        if (Store.downloads.value.any {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }
        ) {
            DownloadService.start(appContext)
        }
    }

    fun refreshNotification() {
        emitAggregate()
    }
}

object NetworkState {
    fun isWifi(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        }.getOrDefault(false)
    }
}
