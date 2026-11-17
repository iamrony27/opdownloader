package com.opdownloader.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import java.io.File

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED;

    val label: String
        get() = when (this) {
            QUEUED -> "Queued"
            DOWNLOADING -> "Downloading"
            PAUSED -> "Paused"
            COMPLETED -> "Completed"
            FAILED -> "Failed"
        }
}

enum class DownloadCategory {
    VIDEO, AUDIO, DOCUMENT, APK, ARCHIVE, OTHER;

    val label: String
        get() = when (this) {
            VIDEO -> "Videos"
            AUDIO -> "Audio"
            DOCUMENT -> "Documents"
            APK -> "APKs"
            ARCHIVE -> "Archives"
            OTHER -> "Other"
        }

    companion object {
        fun fromMime(mime: String?): DownloadCategory {
            val m = mime?.lowercase().orEmpty()
            return when {
                m.startsWith("video/") -> VIDEO
                m.startsWith("audio/") -> AUDIO
                m.contains("android.package") || m.contains("vnd.android.package-archive") -> APK
                m.contains("zip") || m.contains("rar") || m.contains("7z") ||
                    m.contains("tar") || m.contains("gzip") || m.contains("compressed") -> ARCHIVE
                m.startsWith("text/") || m.contains("pdf") || m.contains("word") ||
                    m.contains("excel") || m.contains("powerpoint") || m.contains("document") -> DOCUMENT
                else -> OTHER
            }
        }

        fun fromFileName(name: String): DownloadCategory {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp" -> VIDEO
                "mp3", "m4a", "aac", "opus", "flac", "wav", "ogg", "oga" -> AUDIO
                "apk", "apks", "xapk" -> APK
                "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso" -> ARCHIVE
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub", "json", "xml" -> DOCUMENT
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> OTHER
                else -> OTHER
            }
        }
    }
}

data class DownloadItem(
    val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String = "application/octet-stream",
    val category: DownloadCategory = DownloadCategory.OTHER,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val fileSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speedBps: Long = 0L,
    val etaSeconds: Long = 0L,
    val error: String? = null,
    val fileUri: String? = null,
    val publicPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val ytDlp: Boolean = false,
    val formatSelector: String? = null,
    // Height of the chosen quality, stored so short-lived direct stream URLs
    // (Pornhub HLS) can be refreshed at download time for the same resolution.
    val qualityHeight: Int = 0,
    val audioOnly: Boolean = false,
    val sourceTitle: String? = null,
    val thumbnail: String? = null,
    val pageUrl: String? = null,
    val extractor: String? = null,
    val percent: Float = -1f,
) {
    val progress: Float
        get() = when {
            status == DownloadStatus.COMPLETED -> 1f
            percent >= 0f -> percent.coerceIn(0f, 1f)
            fileSize > 0 -> (downloadedBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }

    val isActive: Boolean get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED

    val isVideo: Boolean get() = category == DownloadCategory.VIDEO
}

data class AppSettings(
    val themeMode: String = "dark",
    val maxConcurrent: Int = 3,
    val wifiOnly: Boolean = false,
    val notifyOnProgress: Boolean = true,
    val autoRetry: Boolean = true,
    val onboarded: Boolean = false,
    val engineSetupDone: Boolean = false,
)

data class QualityOption(
    val id: String,
    val label: String,
    val sublabel: String,
    val sizeBytes: Long = 0L,
    val audioOnly: Boolean = false,
    val selector: String,
    val ext: String = "mp4",
    val height: Int = 0,
)

sealed class AnalysisResult {
    data class Direct(
        val url: String,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val resumable: Boolean,
    ) : AnalysisResult()

    data class Video(
        val url: String,
        val title: String,
        val thumbnail: String?,
        val durationSeconds: Int,
        val extractor: String,
        val formats: List<QualityOption>,
    ) : AnalysisResult()

    data class Failure(val message: String) : AnalysisResult()
}

object AnalysisHolder {
    var url: String = ""
    var result: AnalysisResult? = null
}

fun safeFileName(raw: String, fallback: String = "download"): String {
    val cleaned = raw
        .replace(Regex("""[\u0000-\u001F\u007F]"""), "")
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trimEnd('.', '_')
    if (cleaned.isBlank()) return fallback
    // Limit to ~80 characters and max 120 UTF-8 bytes so filesystem limits (255 bytes max)
    // are never exceeded even when yt-dlp appends .fXXX.mp4.part or other suffixes.
    var truncated = cleaned.take(80)
    while (truncated.toByteArray(Charsets.UTF_8).size > 120 && truncated.isNotEmpty()) {
        truncated = truncated.dropLast(1)
    }
    truncated = truncated.trimEnd('.', '_', ' ')
    return truncated.ifBlank { fallback }
}

fun extensionFromMime(mime: String?): String? {
    if (mime.isNullOrBlank()) return null
    return MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
}

fun fileNameFromUrl(url: String): String {
    val path = runCatching { Uri.parse(url).lastPathSegment }.getOrNull().orEmpty()
    val decoded = runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrDefault(path)
    return safeFileName(decoded.ifBlank { "download" })
}

fun humanBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return if (index == 0) "${value.toLong()} ${units[index]}"
    else String.format("%.1f %s", value, units[index])
}

fun humanSpeed(bytesPerSecond: Long): String = "${humanBytes(bytesPerSecond)}/s"

fun humanDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

fun mediaMimeFor(name: String, fallback: String = "application/octet-stream"): String {
    val ext = name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "opus" -> "audio/ogg"
        "ogg", "oga" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "apk" -> "application/vnd.android.package-archive"
        "zip" -> "application/zip"
        "rar" -> "application/vnd.rar"
        "7z" -> "application/x-7z-compressed"
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "txt" -> "text/plain"
        else -> fallback
    }
}

fun exportRelativePath(category: DownloadCategory): String = when (category) {
    DownloadCategory.VIDEO -> "${Environment.DIRECTORY_MOVIES}/oP Downloader"
    DownloadCategory.AUDIO -> "${Environment.DIRECTORY_MUSIC}/oP Downloader"
    DownloadCategory.OTHER -> "${Environment.DIRECTORY_PICTURES}/oP Downloader"
    else -> "${Environment.DIRECTORY_DOWNLOADS}/oP Downloader"
}

fun partFile(context: Context, id: String): File {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "partial")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "$id.part")
}

fun workFile(context: Context, id: String): File {
    val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "work")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, id)
}
