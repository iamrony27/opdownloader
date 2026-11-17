package com.opdownloader.app.engine

import com.opdownloader.app.data.DownloadItem
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.data.partFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

data class TransferUpdate(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Long,
    val etaSeconds: Long,
)

class DownloadCancelledException : Exception("Cancelled")

/**
 * HTTP(S) downloader with byte-range resume. The partial file survives app restarts,
 * so a paused/interrupted download continues where it stopped.
 */
object DirectDownloader {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0 Mobile Safari/537.36 oP-Downloader/1.0"

    suspend fun download(
        item: DownloadItem,
        onUpdate: suspend (TransferUpdate) -> Unit,
    ): File {
        val target = partFile(AppContextHolder.context, item.id)
        var existing = if (target.exists()) target.length() else 0L

        val requestBuilder = Request.Builder()
            .url(item.url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
        if (existing > 0) {
            requestBuilder.header("Range", "bytes=$existing-")
        }
        val request = requestBuilder.build()
        val call = Net.client.newCall(request)
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        val cancelHandle = job?.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 416 && existing > 0) {
                        target.delete()
                        existing = 0
                        return download(item, onUpdate)
                    }
                    throw IllegalStateException("Server returned HTTP ${response.code} ${response.message}")
                }

                val partial = response.code == 206
                if (existing > 0 && !partial) {
                    // Server ignored the Range header - start over.
                    target.delete()
                    existing = 0
                }

                val contentLength = response.body?.contentLength()?.takeIf { it > 0 } ?: 0L
                val total = when {
                    partial -> parseTotalFromContentRange(response.header("Content-Range")) ?: (existing + contentLength)
                    else -> contentLength
                }
                val startingBytes = if (partial) existing else 0L

                var downloaded = startingBytes
                var lastBytes = startingBytes
                var lastTime = System.currentTimeMillis()
                var lastReport = 0L
                var speed = 0L

                onUpdate(
                    TransferUpdate(
                        downloadedBytes = downloaded,
                        totalBytes = max(total, item.fileSize),
                        speedBps = 0,
                        etaSeconds = 0,
                    )
                )

                val stream = response.body?.byteStream()
                    ?: throw IllegalStateException("The server returned an empty response")
                FileOutputStream(target, !partial).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (!currentCoroutineContext().isActive) throw CancellationException()
                        val read = stream.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastTime >= 500) {
                            speed = ((downloaded - lastBytes) * 1000L / (now - lastTime)).coerceAtLeast(0)
                            lastBytes = downloaded
                            lastTime = now
                        }
                        if (now - lastReport >= 400) {
                            lastReport = now
                            val eta = if (speed > 0 && total > downloaded) (total - downloaded) / speed else 0
                            onUpdate(
                                TransferUpdate(
                                    downloadedBytes = downloaded,
                                    totalBytes = max(total, item.fileSize),
                                    speedBps = speed,
                                    etaSeconds = eta,
                                )
                            )
                        }
                    }
                    output.flush()
                }

                if (total > 0 && target.length() < total) {
                    throw IllegalStateException(
                        "Connection closed early (${humanBytes(target.length())} of ${humanBytes(total)})"
                    )
                }

                onUpdate(
                    TransferUpdate(
                        downloadedBytes = target.length(),
                        totalBytes = if (total > 0) total else target.length(),
                        speedBps = 0,
                        etaSeconds = 0,
                    )
                )
                return target
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) throw CancellationException()
            throw e
        } finally {
            cancelHandle?.dispose()
        }
    }

    private fun parseTotalFromContentRange(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val total = header.substringAfter('/', "").trim()
        return total.toLongOrNull()
    }
}

object AppContextHolder {
    lateinit var context: android.content.Context
}
