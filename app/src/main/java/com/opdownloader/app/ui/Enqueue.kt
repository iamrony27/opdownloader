package com.opdownloader.app.ui

import android.content.Context
import android.widget.Toast
import com.opdownloader.app.data.DownloadCategory
import com.opdownloader.app.data.DownloadItem
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.QualityOption
import com.opdownloader.app.data.mediaMimeFor
import com.opdownloader.app.data.safeFileName
import com.opdownloader.app.engine.DownloadManager
import java.util.UUID

fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

fun enqueueDirectDownload(
    context: Context,
    url: String,
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    categoryHint: DownloadCategory? = null,
): DownloadItem {
    val mime = mimeType.ifBlank { mediaMimeFor(fileName) }
    val category = categoryHint
        ?: DownloadCategory.fromMime(mime).takeIf { it != DownloadCategory.OTHER }
        ?: DownloadCategory.fromFileName(fileName)
    val item = DownloadItem(
        id = UUID.randomUUID().toString(),
        url = url,
        fileName = safeFileName(fileName, "download"),
        mimeType = mime,
        category = category,
        status = DownloadStatus.QUEUED,
        fileSize = sizeBytes,
        pageUrl = url,
    )
    DownloadManager.enqueue(item)
    toast(context, "Download queued")
    return item
}

fun enqueueMediaDownload(
    context: Context,
    pageUrl: String,
    title: String,
    option: QualityOption,
    thumbnail: String? = null,
    extractor: String? = null,
): DownloadItem {
    val base = safeFileName(title, "media")
    val fileName = if (base.lowercase().endsWith(".${option.ext}")) base else "$base.${option.ext}"
    val mime = mediaMimeFor(fileName, if (option.audioOnly) "audio/mpeg" else "video/mp4")
    val item = DownloadItem(
        id = UUID.randomUUID().toString(),
        url = pageUrl,
        fileName = fileName,
        mimeType = mime,
        category = if (option.audioOnly) DownloadCategory.AUDIO else DownloadCategory.VIDEO,
        status = DownloadStatus.QUEUED,
        fileSize = option.sizeBytes,
        ytDlp = true,
        formatSelector = option.selector,
        qualityHeight = option.height,
        audioOnly = option.audioOnly,
        sourceTitle = title,
        thumbnail = thumbnail,
        pageUrl = pageUrl,
        extractor = extractor,
    )
    DownloadManager.enqueue(item)
    toast(context, "Queued ${option.label.lowercase()}")
    return item
}
