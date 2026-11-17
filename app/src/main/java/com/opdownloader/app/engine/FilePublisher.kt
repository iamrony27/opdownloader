package com.opdownloader.app.engine

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.opdownloader.app.data.DownloadCategory
import com.opdownloader.app.data.exportRelativePath
import java.io.File

data class PublishedFile(val uri: Uri, val displayPath: String)

/**
 * Moves a finished temporary file into the public MediaStore so that it is visible
 * in the system Downloads folder / gallery, and gives back a shareable content Uri.
 */
object FilePublisher {

    fun publish(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        category: DownloadCategory,
    ): PublishedFile? {
        if (!source.exists()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishToMediaStore(context, source, displayName, mimeType, category)
                ?: publishToLegacyFolder(context, source, displayName)
        } else {
            publishToLegacyFolder(context, source, displayName)
        }
    }

    private fun collectionFor(mime: String, category: DownloadCategory): Uri {
        val isImage = mime.startsWith("image/")
        return when {
            category == DownloadCategory.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            category == DownloadCategory.AUDIO -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            isImage -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
    }

    private fun publishToMediaStore(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        category: DownloadCategory,
    ): PublishedFile? {
        val resolver = context.contentResolver
        val relative = exportRelativePath(category)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(collectionFor(mimeType, category), values)
        }.getOrNull() ?: run {
            android.util.Log.e("FilePublisher", "insert returned null for $displayName ($mimeType, $relative)")
            return null
        }

        runCatching {
            resolver.openOutputStream(uri, "w").use { out ->
                requireNotNull(out) { "Cannot open output stream" }
                source.inputStream().use { input -> input.copyTo(out, 128 * 1024) }
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }.onFailure {
            android.util.Log.e("FilePublisher", "copy failed for $displayName ($mimeType, $relative)", it)
            runCatching { resolver.delete(uri, null, null) }
            return null
        }

        val path = "$relative/$displayName"
        runCatching { source.delete() }
        return PublishedFile(uri, path)
    }

    private fun publishToLegacyFolder(
        context: Context,
        source: File,
        displayName: String,
    ): PublishedFile? {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "oP Downloader")
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, displayName)
        runCatching { source.copyTo(target, overwrite = true) }.onFailure { return null }
        runCatching { source.delete() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return PublishedFile(uri, target.absolutePath)
    }

    fun openFile(context: Context, uriString: String?, mimeType: String, fallbackPath: String? = null): Boolean {
        val uri = resolveUri(context, uriString, fallbackPath) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun shareFile(context: Context, uriString: String?, mimeType: String, title: String, fallbackPath: String? = null): Boolean {
        val uri = resolveUri(context, uriString, fallbackPath) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share file").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    /**
     * Checks whether a published download is still present on the phone. A file
     * removed from the Files/Gallery app leaves the history entry behind, so the
     * UI needs this to show a "file deleted" state instead of a broken Open button.
     */
    fun fileExists(context: Context, uriString: String?, fallbackPath: String?): Boolean {
        if (!uriString.isNullOrBlank()) {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                val readable = runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                }.getOrDefault(false)
                if (readable) return true
            } else if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                if (runCatching { File(uri.path!!).exists() }.getOrDefault(false)) return true
            }
        }
        if (!fallbackPath.isNullOrBlank()) {
            if (runCatching { File(fallbackPath).exists() }.getOrDefault(false)) return true
        }
        return false
    }

    fun deleteFile(context: Context, uriString: String?, fallbackPath: String?): Boolean {
        var deleted = false
        if (!uriString.isNullOrBlank()) {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") {
                deleted = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
            }
        }
        if (!deleted && !fallbackPath.isNullOrBlank()) {
            deleted = runCatching { File(fallbackPath).delete() }.getOrDefault(false)
        }
        return deleted
    }

    private fun resolveUri(context: Context, uriString: String?, fallbackPath: String?): Uri? {
        if (!uriString.isNullOrBlank()) {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") return uri
            if (uri.scheme == "file" && uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists()) {
                    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }
            }
        }
        if (!fallbackPath.isNullOrBlank()) {
            val file = File(fallbackPath)
            if (file.exists()) {
                return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
        return null
    }
}
