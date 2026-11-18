package com.opdownloader.app.engine

import android.content.Context
import com.opdownloader.app.BuildConfig
import com.opdownloader.app.data.AnalysisResult
import com.opdownloader.app.data.DownloadItem
import com.opdownloader.app.data.QualityOption
import com.opdownloader.app.data.fileNameFromUrl
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.data.mediaMimeFor
import com.opdownloader.app.data.safeFileName
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

sealed class EngineState {
    data object Idle : EngineState()
    data object Preparing : EngineState()
    data object Ready : EngineState()
    data class Failed(val message: String) : EngineState()
}

data class DownloadedMedia(val file: File, val displayName: String)

/** Live transfer sample reported by the yt-dlp runner. */
data class TransferProgress(
    val percent: Float,
    val etaSeconds: Long,
    val speedBps: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
)

/**
 * On-device media extraction powered by the bundled yt-dlp + Python + FFmpeg runtime
 * (same engine family used by FetchJet). Works for YouTube, Instagram, TikTok and 1000+ sites.
 */
object MediaTools {
    private val lock = Any()
    private val initializing = AtomicBoolean(false)
    @Volatile private var initialized = false

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private fun ensureInit(context: Context) {
        if (initialized) return
        synchronized(lock) {
            if (initialized) return
            if (!initializing.compareAndSet(false, true)) {
                // Another thread is preparing the runtime; wait for it.
                while (initializing.get() && !initialized) {
                    Thread.sleep(120)
                }
                if (initialized) return
            }
            try {
                _state.value = EngineState.Preparing
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                initialized = true
                _state.value = EngineState.Ready
            } catch (e: Exception) {
                _state.value = EngineState.Failed(e.message ?: "Could not prepare the media engine")
                throw e
            } finally {
                initializing.set(false)
            }
        }
    }

    suspend fun prepare(context: Context) = withContext(Dispatchers.IO) {
        runCatching { ensureInit(context) }
        Unit
    }

    fun engineVersion(context: Context): String? = runCatching {
        (YoutubeDL.versionName(context) ?: YoutubeDL.version(context))
            ?.removePrefix("yt-dlp ")
            ?.trim()
            ?.ifBlank { null }
    }.getOrNull()

    suspend fun updateEngine(context: Context): String = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit(context)
            val result = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            val version = engineVersion(context).orEmpty()
            when (result) {
                YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated${if (version.isBlank()) "" else " to $version"}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date${if (version.isBlank()) "" else " ($version)"}"
                else -> "Update finished"
            }
        }.getOrElse { "Update failed: ${shortError(it)}" }
    }

    /** Like [prepare], but throws so first-run setup can surface and retry failures. */
    suspend fun prepareStrict(context: Context) = withContext(Dispatchers.IO) {
        ensureInit(context)
    }

    /** Like [updateEngine], but throws instead of returning a failure message. */
    suspend fun updateEngineStrict(context: Context): String = withContext(Dispatchers.IO) {
        ensureInit(context)
        val result = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        val version = engineVersion(context).orEmpty()
        val suffix = if (version.isBlank()) "" else " ($version)"
        when (result) {
            YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated$suffix"
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is up to date$suffix"
            else -> "yt-dlp update finished$suffix"
        }
    }

    suspend fun analyze(context: Context, url: String): AnalysisResult = withContext(Dispatchers.IO) {
        val targetUrl = if (isPornhubUrl(url)) normalizePornhubUrl(url) else url
        if (targetUrl.contains("videoplayback") || targetUrl.contains("googlevideo.com")) {
            val uri = runCatching { android.net.Uri.parse(targetUrl) }.getOrNull()
            val id = uri?.getQueryParameter("id") ?: uri?.getQueryParameter("docid")
            if (!id.isNullOrBlank()) {
                val watchUrl = "https://www.youtube.com/watch?v=$id"
                val res = runCatching { analyze(context, watchUrl) }.getOrNull()
                if (res is AnalysisResult.Video) return@withContext res
            }
            val clen = uri?.getQueryParameter("clen")?.toLongOrNull() ?: 0L
            val itag = uri?.getQueryParameter("itag").orEmpty()
            val mimeParam = uri?.getQueryParameter("mime") ?: "video/mp4"
            val ext = if (mimeParam.contains("webm")) "webm" else if (mimeParam.contains("audio")) "m4a" else "mp4"
            return@withContext AnalysisResult.Direct(
                url = targetUrl,
                fileName = "YouTube_Media_${itag.ifEmpty { "stream" }}.$ext",
                mimeType = mimeParam,
                sizeBytes = clen,
                resumable = true,
            )
        }

        // Video sites always need yt-dlp; the HEAD probe only wastes a round trip
        // (and can hang for ~30s on a blocked host). Probe only generic links.
        val head = if (isKnownMediaPage(targetUrl)) null else runCatching { probe(targetUrl) }.getOrNull()
        val mime = head?.mimeType.orEmpty()
        val isMediaMime = mime.startsWith("video/") || mime.startsWith("audio/")
        val isFileMime = isMediaMime || mime.startsWith("image/") || listOf(
            "application/pdf", "application/zip", "application/x-7z-compressed",
            "application/vnd.rar", "application/octet-stream",
            "application/vnd.android.package-archive", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain", "application/epub+zip"
        ).any { mime.startsWith(it, ignoreCase = true) }
        val isHls = mime.contains("mpegurl", true) || targetUrl.substringBefore('?').endsWith(".m3u8", true)
        val isDash = mime.contains("dash+xml", true) || targetUrl.substringBefore('?').endsWith(".mpd", true)
        val hasAttachment = head?.contentDisposition?.contains("attachment", true) == true
        val knownExtension = targetUrl.substringBefore('?').substringAfterLast('.', "")
            .lowercase() in setOf(
            "mp4", "mkv", "webm", "avi", "mov", "mp3", "m4a", "aac", "flac", "wav", "ogg",
            "apk", "zip", "rar", "7z", "pdf", "docx", "xlsx", "pptx"
        )

        if (!isHls && !isDash && head != null && (isFileMime || hasAttachment || knownExtension) &&
            !mime.startsWith("text/html", true)
        ) {
            return@withContext AnalysisResult.Direct(
                url = targetUrl,
                fileName = head.fileName ?: fileNameFromUrl(targetUrl),
                mimeType = mime.ifBlank { mediaMimeFor(fileNameFromUrl(targetUrl)) },
                sizeBytes = head.size,
                resumable = head.resumable,
            )
        }

        // The head probe was blocked but the link obviously points at a file: let the
        // download engine try anyway instead of failing before we even start.
        if (head == null && knownExtension && !isHls && !isDash) {
            val name = fileNameFromUrl(targetUrl)
            return@withContext AnalysisResult.Direct(
                url = targetUrl,
                fileName = name,
                mimeType = mediaMimeFor(name),
                sizeBytes = 0L,
                resumable = false,
            )
        }

        // For Pornhub URLs, direct extraction bypasses Cloudflare/bot blocks reliably
        if (isPornhubUrl(targetUrl)) {
            val directResult = runCatching { analyzePornhubDirect(targetUrl) }.getOrNull()
            if (directResult != null) {
                return@withContext directResult
            }
        }

        // Not a plain file - ask yt-dlp.
        val infoResult = runCatching { info(context, targetUrl) }
        val info = infoResult.getOrNull()
        if (info != null && !info.formats.isNullOrEmpty()) {
            return@withContext AnalysisResult.Video(
                url = targetUrl,
                title = (info.title ?: info.fulltitle ?: "Video").trim(),
                thumbnail = info.thumbnail,
                durationSeconds = info.duration,
                extractor = info.extractorKey ?: info.extractor ?: "yt-dlp",
                formats = buildFormats(info),
            )
        }

        // Fall back to a direct download if the server tells us it is a file anyway.
        if (head != null && head.size > 0 && isMediaMime) {
            return@withContext AnalysisResult.Direct(
                url = url,
                fileName = head.fileName ?: fileNameFromUrl(url),
                mimeType = mime,
                sizeBytes = head.size,
                resumable = head.resumable,
            )
        }

        val detail = infoResult.exceptionOrNull()?.let { shortError(it) }
            ?: "No downloadable media was found on this page."
        AnalysisResult.Failure(
            when {
                detail.contains("Unsupported URL", true) ->
                    "This link is not a direct file and this site is not supported by the media engine."
                detail.isBlank() -> "The analyzer did not return any details for this link. Try the Web Sniffer."
                else -> detail
            }
        )
    }

    private data class Probe(
        val fileName: String?,
        val mimeType: String,
        val size: Long,
        val resumable: Boolean,
        val contentDisposition: String?,
    )

    private fun probe(url: String): Probe? {
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .build()
        var response = runCatching { Net.probeClient.newCall(headRequest).execute() }.getOrNull()
        if (response == null || !response.isSuccessful || response.code == 405 || response.code == 403) {
            response?.close()
            val getRequest = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Range", "bytes=0-0")
                .build()
            response = runCatching { Net.probeClient.newCall(getRequest).execute() }.getOrNull()
        }
        return response?.use { resp ->
            if (!resp.isSuccessful && resp.code != 206) return@use null
            val cd = resp.header("Content-Disposition")
            val mime = resp.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
            val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L
            val rangeTotal = resp.header("Content-Range")?.substringAfter('/', "")?.trim()?.toLongOrNull()
            Probe(
                fileName = fileNameFromDisposition(cd),
                mimeType = mime,
                size = rangeTotal ?: contentLength,
                resumable = resp.header("Accept-Ranges")?.contains("bytes", true) == true || resp.code == 206 ||
                    rangeTotal != null,
                contentDisposition = cd,
            )
        }
    }

    fun isPornhubUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("pornhub.com") ||
            lower.contains("pornhubpremium.com") ||
            lower.contains("phub.to") ||
            lower.contains("phncdn.com") ||
            lower.contains("sabtpledge.com") ||
            lower.contains("thumbzilla.com")
    }

    fun normalizePornhubUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val urlRegex = Regex("""https?://[^\s<>"']+""")
        val url = urlRegex.find(trimmed)?.value ?: trimmed

        val originalHost = runCatching { url.toHttpUrlOrNull()?.host }.getOrNull()
            ?.takeIf { it.contains("pornhub") || it.contains("phub") }
            ?: "www.pornhub.com"

        val viewkeyRegex = Regex("""[?&]viewkey=([a-zA-Z0-9]+)""")
        val embedRegex = Regex("""/embed/([a-zA-Z0-9]+)""")
        val viewkey = viewkeyRegex.find(url)?.groupValues?.getOrNull(1)
            ?: embedRegex.find(url)?.groupValues?.getOrNull(1)

        if (!viewkey.isNullOrBlank()) {
            return "https://$originalHost/view_video.php?viewkey=$viewkey"
        }

        val tzRegex = Regex("""thumbzilla\.com/video/([a-zA-Z0-9]+)""")
        val tzId = tzRegex.find(url)?.groupValues?.getOrNull(1)
        if (!tzId.isNullOrBlank()) {
            return "https://www.thumbzilla.com/video/$tzId"
        }

        return url
    }

    private data class HlsProbe(val selector: String, val sizeBytes: Long, val liveEdge: Boolean)

    /**
     * Resolves an HLS master playlist into the exact variant for one height and
     * estimates its bytes from BANDWIDTH. This makes the picker show the size of
     * the stream that actually gets downloaded, and lets us drop sliding-window
     * live playlists instead of offering them as complete videos.
     */
    private fun probeHlsPlaylist(url: String, height: Int, durationSec: Int): HlsProbe? {
        val req = runCatching {
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.pornhub.com/")
                .header("Origin", "https://www.pornhub.com")
                .build()
        }.getOrNull() ?: return null
        val body = runCatching {
            Net.probeClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }
        }.getOrDefault("")
        if (body.isBlank() || !body.contains("#EXTM3U")) return null

        // A media-sequence greater than zero with no ENDLIST is a live sliding
        // window. Downloading it would silently produce a truncated clip.
        val sequence = Regex("""#EXT-X-MEDIA-SEQUENCE:(\d+)""")
            .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        if (sequence > 0 && !body.contains("#EXT-X-ENDLIST")) {
            return HlsProbe(url, 0L, true)
        }

        val lines = body.lineSequence().toList()
        var bestBandwidth = 0L
        var bestUrl = url
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF")) continue
            val bandwidth = Regex("""BANDWIDTH=(\d+)""")
                .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
            val w = resolution?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val h = resolution?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            if (w <= 0 || h <= 0 || minOf(w, h) != height) continue
            val child = lines.getOrNull(i + 1)?.trim().orEmpty()
            if (child.isBlank() || child.startsWith("#")) continue
            val resolved = runCatching {
                url.toHttpUrlOrNull()?.newBuilder(child)?.build()?.toString()
            }.getOrNull() ?: child
            if (bandwidth > bestBandwidth) {
                bestBandwidth = bandwidth
                bestUrl = resolved
            }
        }
        val bytes = if (bestBandwidth > 0 && durationSec > 0) {
            bestBandwidth * durationSec.toLong() / 8L
        } else 0L
        return HlsProbe(bestUrl, bytes, false)
    }

    private fun analyzePornhubDirect(url: String): AnalysisResult? {
        val viewkeyRegex = Regex("""[?&]viewkey=([a-zA-Z0-9]+)""")
        val embedRegex = Regex("""/embed/([a-zA-Z0-9]+)""")
        val viewkey = viewkeyRegex.find(url)?.groupValues?.getOrNull(1)
            ?: embedRegex.find(url)?.groupValues?.getOrNull(1)
            ?: return null

        val originalHost = runCatching { url.toHttpUrlOrNull()?.host }.getOrNull()
            ?.takeIf { it.contains("pornhub") || it.contains("phub") }
            ?: "www.pornhub.com"

        val pageUrl = "https://$originalHost/view_video.php?viewkey=$viewkey"

        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://$originalHost/")
            .header("Cookie", "age_verified=1; accessAgeDisclaimerPH=1; platform=pc")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val response = runCatching { Net.client.newCall(request).execute() }.getOrNull() ?: return null
        if (!response.isSuccessful) {
            response.close()
            return null
        }

        val html = response.body?.string().orEmpty()
        response.close()
        if (html.isBlank()) return null

        val flashvarsMatch = Regex("""var\s+flashvars_\d+\s*=\s*(\{.+?\});\s*(?:var|</script>)""", RegexOption.DOT_MATCHES_ALL).find(html)
            ?: Regex("""flashvars_\d+\s*=\s*(\{.+?\});""").find(html)

        val flashvarsJson = flashvarsMatch?.groupValues?.getOrNull(1)
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()

        var title = ""
        var thumbnail: String? = null
        var duration = 0
        val formats = ArrayList<QualityOption>()

        if (!flashvarsJson.isNullOrBlank()) {
            val root = runCatching { mapper.readTree(flashvarsJson) }.getOrNull()
            if (root != null) {
                title = root.path("video_title").asText("").trim()
                thumbnail = root.path("image_url").asText(null)
                duration = root.path("video_duration").asInt(0)

                val mediaDefs = root.path("mediaDefinitions")
                if (mediaDefs.isArray) {
                    val seenHeights = HashSet<Int>()
                    for (def in mediaDefs) {
                        val format = def.path("format").asText("")
                        val videoUrl = def.path("videoUrl").asText("")
                        if (format.equals("hls", ignoreCase = true) && videoUrl.startsWith("http")) {
                            val h = def.path("height").asInt(0).let {
                                if (it > 0) it else def.path("quality").asText("").filter { c -> c.isDigit() }.toIntOrNull() ?: 0
                            }
                            if (h > 0 && seenHeights.add(h)) {
                                // Resolve the master playlist to one exact variant.
                                // Otherwise yt-dlp grabs the highest rendition and
                                // both the resolution and the size are wrong.
                                val probe = probeHlsPlaylist(videoUrl, h, duration)
                                if (probe?.liveEdge == true) continue // sliding-window live stream, not a complete video
                                val resolvedUrl = probe?.selector ?: videoUrl
                                val estimated = probe?.sizeBytes ?: 0L
                                formats.add(
                                    QualityOption(
                                        id = "ph_$h",
                                        label = qualityLabel(h),
                                        sublabel = buildString {
                                            append(h).append("p • MP4")
                                            if (estimated > 0) append(" • ~").append(humanBytes(estimated))
                                        },
                                        sizeBytes = estimated,
                                        audioOnly = false,
                                        selector = resolvedUrl,
                                        ext = "mp4",
                                        height = h,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        if (title.isBlank()) {
            title = Regex("""<h1[^>]*class=["\'][^"\']*title[^"\']*["\'][^>]*>(.*?)</h1>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
        }
        if (title.isBlank()) {
            title = Regex("""<meta[^>]*property=["\']og:title["\'][^>]*content=["\']([^"\']+)["\']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        }
        if (title.isBlank()) title = "Pornhub Video $viewkey"

        if (thumbnail.isNullOrBlank()) {
            thumbnail = Regex("""<meta[^>]*property=["\']og:image["\'][^>]*content=["\']([^"\']+)["\']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)?.trim()
        }

        if (formats.isEmpty()) return null

        formats.sortByDescending { it.height }

        val bestUrl = formats.first().selector
        val bestSize = formats.first().sizeBytes
        val resultFormats = ArrayList<QualityOption>()
        resultFormats.addAll(formats)
        resultFormats.add(
            QualityOption(
                id = "best",
                label = "Best available",
                sublabel = "Highest quality • MP4",
                sizeBytes = bestSize,
                selector = bestUrl,
                ext = "mp4",
            )
        )
        resultFormats.add(
            QualityOption(
                id = "audio-mp3",
                label = "Audio only (MP3)",
                sublabel = "Original audio • MP3",
                sizeBytes = 0L,
                audioOnly = true,
                selector = bestUrl,
                ext = "mp3",
            )
        )

        return AnalysisResult.Video(
            url = pageUrl,
            title = title,
            thumbnail = thumbnail,
            durationSeconds = duration,
            extractor = "Pornhub",
            formats = resultFormats,
        )
    }

    private fun info(context: Context, url: String): VideoInfo? {
        val targetUrl = if (isPornhubUrl(url)) normalizePornhubUrl(url) else url
        val started = System.currentTimeMillis()
        // Re-analyzing the same link within the TTL is instant: reuse the saved
        // extraction instead of asking yt-dlp again.
        readInfoCache(context, targetUrl)?.let {
            android.util.Log.d("OpPerf", "analyze cache hit in ${System.currentTimeMillis() - started}ms")
            return it
        }

        ensureInit(context)
        val request = YoutubeDLRequest(targetUrl)
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        request.addOption("--dump-single-json")
        request.addOption("--user-agent", USER_AGENT)
        if (isPornhubUrl(targetUrl)) {
            request.addOption("--referer", "https://www.pornhub.com/")
            request.addOption("--add-header", "Origin:https://www.pornhub.com")
            request.addOption("--add-header", "Cookie:age_verified=1; accessAgeDisclaimerPH=1; accessAgeDisclaimerUK=1; accessPH=1; platform=pc")
        }
        val response = YoutubeDL.getInstance().execute(request)
        val json = extractJson(response.out) ?: return null
        val info = YoutubeDL.getInstance().objectMapper.readValue(json, VideoInfo::class.java)
        runCatching { writeInfoCache(context, targetUrl, json) }
        android.util.Log.d("OpPerf", "analyze network extraction in ${System.currentTimeMillis() - started}ms")
        return info
    }

    /** Hosts that are always media pages, so the HEAD probe can be skipped. */
    private val knownMediaHosts = listOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com", "vimeo.com",
        "dailymotion.com", "facebook.com", "fb.watch", "fb.me", "instagram.com",
        "twitter.com", "x.com", "tiktok.com", "soundcloud.com", "twitch.tv",
        "reddit.com", "bilibili.com", "rumble.com", "odysee.com",
        "streamable.com", "vk.com", "ok.ru", "spotify.com", "pinterest.com",
        "pornhub.com", "pornhubpremium.com", "phub.to", "thumbzilla.com",
        "phncdn.com", "sabtpledge.com", "xvideos.com", "xhamster.com",
    )

    private fun isKnownMediaPage(url: String): Boolean {
        val host = runCatching {
            url.toHttpUrlOrNull()?.host?.lowercase()
        }.getOrNull() ?: return false
        return knownMediaHosts.any { host == it || host.endsWith(".$it") }
    }

    // ---- yt-dlp info cache -------------------------------------------------
    // `--dump-single-json` output is stored per URL, then fed back to the
    // downloader with `--load-info-json` so starting a download skips a second
    // (slow) extraction.

    private const val INFO_TTL_MS = 2 * 60 * 60 * 1000L

    private fun infoCacheFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "yt_info").apply { mkdirs() }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "${digest.take(40)}.json")
    }

    private fun readInfoCache(context: Context, url: String): VideoInfo? = runCatching {
        val file = infoCacheFile(context, url)
        if (!file.isFile) return@runCatching null
        if (file.length() < 64) {
            file.delete()
            return@runCatching null
        }
        if (System.currentTimeMillis() - file.lastModified() > INFO_TTL_MS) {
            file.delete()
            return@runCatching null
        }
        YoutubeDL.getInstance().objectMapper
            .readValue(file.readText(), VideoInfo::class.java)
    }.getOrNull()

    private fun writeInfoCache(context: Context, url: String, json: String) {
        val file = infoCacheFile(context, url)
        // Keep the cache folder bounded: drop expired extractions as we write.
        runCatching {
            file.parentFile?.listFiles()?.forEach {
                if (it.isFile && System.currentTimeMillis() - it.lastModified() > INFO_TTL_MS) it.delete()
            }
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(file)) {
            file.writeText(json)
            tmp.delete()
        }
    }

    /** Returns the freshest cache file for a URL, or null when missing/stale. */
    private fun freshInfoCache(context: Context, url: String): File? {
        val file = infoCacheFile(context, url)
        if (!file.isFile || file.length() < 64) return null
        if (System.currentTimeMillis() - file.lastModified() > INFO_TTL_MS) return null
        return file
    }

    private fun extractJson(out: String?): String? {
        if (out.isNullOrBlank()) return null
        val start = out.indexOf('{')
        val end = out.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return out.substring(start, end + 1)
    }

    private fun buildFormats(info: VideoInfo): List<QualityOption> {
        val formats = info.formats.orEmpty()
        val result = ArrayList<QualityOption>()
        val isPh = (info.webpageUrl?.let { isPornhubUrl(it) } == true) ||
            (info.extractorKey ?: info.extractor).orEmpty().contains("pornhub", ignoreCase = true)
        val isFb = (info.webpageUrl.orEmpty().contains("facebook.com", ignoreCase = true)) ||
            (info.webpageUrl.orEmpty().contains("fb.watch", ignoreCase = true)) ||
            (info.extractorKey ?: info.extractor).orEmpty().contains("facebook", ignoreCase = true)

        fun extractHeight(f: VideoFormat): Int {
            if (f.height > 0 && f.width > 0) {
                return minOf(f.height, f.width)
            }
            if (f.height > 0) {
                return when (f.height) {
                    1920 -> 1080
                    1280 -> 720
                    960 -> 540
                    640 -> 360
                    else -> f.height
                }
            }
            val fid = f.formatId.orEmpty().lowercase()
            if (fid == "hd") return 720
            if (fid == "sd") return 360
            val hint = "${f.formatNote.orEmpty()} ${f.formatId.orEmpty()}"
            val match = Regex("""\b(2160|1440|1080|720|540|480|360|240)\b""").find(hint)
            return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        }

        // The old estimate took the BIGGEST file at each height (usually h264),
        // but bestvideo actually downloads the preferred codec (usually AV1/VP9),
        // which is much smaller. Pick the exact format once and use it for BOTH the
        // displayed size and the download selector so they always agree.
        fun sizeOf(f: VideoFormat): Long {
            val exact = maxOf(f.fileSize, f.fileSizeApproximate)
            if (exact > 0) return exact
            val kbps = maxOf(f.tbr, f.abr)
            return if (kbps > 0 && info.duration > 0) {
                (kbps.toLong() * 1000L / 8L) * info.duration.toLong()
            } else 0L
        }

        fun videoRank(f: VideoFormat): Int = when {
            f.vcodec?.startsWith("av01", true) == true -> 4
            f.vcodec?.startsWith("vp9", true) == true || f.vcodec?.startsWith("vp09", true) == true -> 3
            f.vcodec?.startsWith("h264", true) == true || f.vcodec?.startsWith("avc", true) == true -> 2
            f.vcodec.isNullOrBlank() || f.vcodec == "none" -> -1
            else -> 1
        }

        fun audioRank(f: VideoFormat): Int = when {
            f.acodec?.contains("opus", true) == true -> 4
            f.acodec?.contains("vorbis", true) == true -> 3
            f.acodec?.contains("mp4a", true) == true -> 2
            f.acodec.isNullOrBlank() || f.acodec == "none" -> -1
            else -> 1
        }

        val bestAudio = formats
            .filter { audioRank(it) > 0 && sizeOf(it) > 0 }
            .sortedWith(compareBy<VideoFormat> { audioRank(it) }.thenBy { sizeOf(it) })
            .lastOrNull()
        val audioSelector = bestAudio?.formatId?.let { "$it/bestaudio/best" } ?: "bestaudio/best"
        val audioBytes = bestAudio?.let(::sizeOf) ?: 0L

        val heights = formats
            .filter {
                val h = extractHeight(it)
                val isExplicit = it.formatId.equals("hd", true) || it.formatId.equals("sd", true)
                h > 0 && (isPh || isFb || isExplicit || (it.vcodec != "none" && !it.vcodec.isNullOrBlank()))
            }
            .map(::extractHeight)
            .distinct()
            .sortedDescending()

        heights.take(8).forEach { height ->
            val matching = formats.filter { extractHeight(it) == height && videoRank(it) > 0 }
            val video = matching
                .sortedWith(compareBy<VideoFormat> { videoRank(it) }.thenBy { it.fps }.thenBy { sizeOf(it) })
                .lastOrNull()
            val videoBytes = video?.let(::sizeOf) ?: 0L
            val needsAudio = video != null && bestAudio != null &&
                (video.acodec.isNullOrBlank() || video.acodec == "none")
            val estimated = if (videoBytes > 0) videoBytes + if (needsAudio) audioBytes else 0L else 0L

            val otherDim = (height * 16 + 8) / 9 + 40
            val generic = if (isPh) {
                "best[height<=$height]/bestvideo[height<=$height]+bestaudio/best"
            } else {
                "bestvideo[height<=$height][width<=$otherDim]+bestaudio" +
                    "/bestvideo[width<=$height][height<=$otherDim]+bestaudio" +
                    "/bestvideo[height<=$height]+bestaudio" +
                    "/bestvideo[width<=$height]+bestaudio" +
                    "/best[height<=$height]/best"
            }
            // Exact format ids first so the bytes shown are the bytes downloaded.
            // The generic filter stays as a fallback if a cached extraction expires
            // and yt-dlp re-parses the page with new format ids.
            val selector = buildList {
                if (video != null && needsAudio) {
                    val vid = video.formatId
                    val aid = bestAudio?.formatId
                    if (!vid.isNullOrBlank() && !aid.isNullOrBlank()) add("$vid+$aid")
                }
                if (!video?.formatId.isNullOrBlank()) add(video!!.formatId!!)
                add(generic)
            }.joinToString("/")

            result.add(
                QualityOption(
                    id = "h$height",
                    label = qualityLabel(height),
                    sublabel = buildString {
                        append(height).append("p")
                        val fps = video?.fps?.takeIf { it > 0 } ?: matching.maxOfOrNull { it.fps } ?: 0
                        if (fps > 0) append(" • ").append(fps).append("fps")
                        append(" • MP4")
                    },
                    sizeBytes = estimated,
                    audioOnly = false,
                    selector = selector,
                    ext = "mp4",
                    height = height,
                )
            )
        }

        result.add(
            QualityOption(
                id = "best",
                label = "Best available",
                sublabel = "Highest quality • MP4",
                sizeBytes = result.firstOrNull()?.sizeBytes ?: 0L,
                selector = if (isPh) "best/bestvideo+bestaudio" else "bestvideo+bestaudio/best",
                ext = "mp4",
            )
        )

        result.add(
            QualityOption(
                id = "audio-mp3",
                label = "Audio only (MP3)",
                sublabel = "320 kbps • MP3",
                sizeBytes = audioBytes,
                audioOnly = true,
                selector = audioSelector,
                ext = "mp3",
            )
        )
        result.add(
            QualityOption(
                id = "audio-m4a",
                label = "Audio only (M4A)",
                sublabel = "Original audio • M4A",
                sizeBytes = audioBytes,
                audioOnly = true,
                selector = audioSelector,
                ext = "m4a",
            )
        )
        return result
    }
    private fun qualityLabel(height: Int): String = when {
        height >= 2160 -> "2160p • 4K"
        height >= 1440 -> "1440p • 2K"
        height >= 1080 -> "1080p • Full HD"
        height >= 720 -> "720p • HD"
        height >= 540 -> "540p"
        height >= 480 -> "480p • SD"
        height >= 360 -> "360p"
        else -> "${height}p"
    }

    /**
     * Re-reads the video page and returns a fresh HLS URL for the quality the user
     * originally picked. Pornhub stream URLs are signed and expire quickly, so the
     * one captured during analysis is often dead by the time the download starts.
     * Falls back to the highest available stream when the exact height is gone.
     */
    private fun freshPornhubSelector(item: DownloadItem): String? {
        val result = analyzePornhubDirect(item.url) as? AnalysisResult.Video ?: return null
        val wanted = item.qualityHeight
        val streams = result.formats.filter { !it.audioOnly && it.height > 0 }
        val chosen = if (wanted > 0) {
            streams.firstOrNull { it.height == wanted }
                ?: streams.filter { it.height <= wanted }.maxByOrNull { it.height }
                ?: streams.maxByOrNull { it.height }
        } else {
            streams.maxByOrNull { it.height }
        } ?: result.formats.firstOrNull { it.id == "best" }
        return chosen?.selector?.takeIf { it.startsWith("http", ignoreCase = true) }
    }

    suspend fun download(
        context: Context,
        item: DownloadItem,
        onProgress: (TransferProgress) -> Unit,
    ): DownloadedMedia = withContext(Dispatchers.IO) {
        ensureInit(context)
        clearCancelled(item.id)
        val outDir = File(context.cacheDir, "ytdlp_${item.id}")
        outDir.mkdirs()

        val base = safeFileName(
            item.fileName.substringBeforeLast('.', item.fileName).replace("%", "_"),
            "download"
        )

        val startedDownload = System.currentTimeMillis()
        android.util.Log.d("OpPerf", "download start item=${item.id} url=${item.url}")

        // Pornhub hands out signed HLS URLs that expire within minutes. The URL
        // saved at analysis time is often already stale (HTTP 410) by the time the
        // user picks a quality and the download starts. Re-read the page's flashvars
        // now and swap in a fresh URL for the same resolution before downloading.
        var effectiveItem = item
        if (isPornhubUrl(item.url) && item.formatSelector?.startsWith("http", ignoreCase = true) == true) {
            val fresh = runCatching { freshPornhubSelector(item) }.getOrNull()
            if (fresh != null) {
                android.util.Log.d("OpPerf", "refreshed Pornhub stream URL for ${item.id}")
                effectiveItem = item.copy(formatSelector = fresh)
            } else {
                android.util.Log.d("OpPerf", "Pornhub URL refresh failed; using stored URL for ${item.id}")
            }
        }

        // Analysis already extracted this page's formats and cached the JSON. Feed
        // it back with --load-info-json so yt-dlp starts downloading immediately
        // instead of fetching/parsing the whole page a second time. This removes
        // the long pause between tapping Download and the first byte arriving.
        val cachedInfo = if (effectiveItem.formatSelector?.startsWith("http", ignoreCase = true) == true) {
            null // direct stream URLs have no info cache
        } else {
            val analysisUrl = if (isPornhubUrl(effectiveItem.url)) normalizePornhubUrl(effectiveItem.url) else effectiveItem.url
            freshInfoCache(context, analysisUrl)
        }
        if (cachedInfo != null) {
            android.util.Log.d("OpPerf", "reusing cached info json for ${item.id}")
        }

        try {
            try {
                val request = buildYtDlpRequest(effectiveItem, outDir, base).apply {
                    if (cachedInfo != null) addOption("--load-info-json", cachedInfo.absolutePath)
                }
                runYtDlpAttempt(effectiveItem, outDir, request, onProgress)
            } catch (e: Exception) {
                if (cachedInfo == null || e is kotlinx.coroutines.CancellationException ||
                    e is YoutubeDL.CanceledException || isCancelled(item.id)
                ) {
                    throw e
                }
                // Signed media URLs in the cache can expire. Drop the stale info
                // and let yt-dlp extract fresh data instead of failing the download.
                android.util.Log.d("OpPerf", "cached info failed (${shortError(e)}); retrying with fresh extraction")
                runCatching { cachedInfo.delete() }
                runYtDlpAttempt(effectiveItem, outDir, buildYtDlpRequest(effectiveItem, outDir, base), onProgress)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: YoutubeDL.CanceledException) {
            throw kotlinx.coroutines.CancellationException("Download canceled by user", e)
        } catch (e: Exception) {
            if (isCancelled(item.id)) {
                throw kotlinx.coroutines.CancellationException("Download canceled by user", e)
            }
            throw e
        }
        android.util.Log.d("OpPerf", "download engine finished in ${System.currentTimeMillis() - startedDownload}ms")

        val produced = outDir.walkTopDown()
            .filter { it.isFile && it.length() > 0 }
            .filterNot { it.name.endsWith(".part") || it.name.endsWith(".ytdl") || it.name.startsWith(".") }
            .maxByOrNull { it.length() }
            ?: throw IllegalStateException("The media engine finished but produced no file")

        DownloadedMedia(produced, produced.name)
    }

    private fun buildYtDlpRequest(
        item: DownloadItem,
        outDir: File,
        base: String,
    ): YoutubeDLRequest {
        val isDirectStream = item.formatSelector?.startsWith("http", ignoreCase = true) == true
        val targetUrl = if (isDirectStream) {
            item.formatSelector!!
        } else if (isPornhubUrl(item.url)) {
            normalizePornhubUrl(item.url)
        } else {
            item.url
        }

        val request = YoutubeDLRequest(targetUrl)
        request.addOption("-o", File(outDir, "$base.%(ext)s").absolutePath)
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("--newline")
        request.addOption("--no-warnings")
        request.addOption("--continue")

        val isPh = isPornhubUrl(targetUrl) || isPornhubUrl(item.url) || isPornhubUrl(item.pageUrl.orEmpty()) ||
            targetUrl.contains("phncdn.com", true) || targetUrl.contains("sabtpledge.com", true)
        if (isPh) {
            request.addOption("--referer", "https://www.pornhub.com/")
            request.addOption("--add-header", "Origin:https://www.pornhub.com")
            request.addOption("--user-agent", USER_AGENT)
            request.addOption("--hls-prefer-ffmpeg")
            request.addOption("--fragment-retries", "10")
            request.addOption("--concurrent-fragments", "3")
        } else {
            request.addOption("--user-agent", USER_AGENT)
            request.addOption("--hls-prefer-ffmpeg")
            request.addOption("--fragment-retries", "4")
            request.addOption("--concurrent-fragments", "2")
        }

        if (item.audioOnly) {
            if (!isDirectStream) {
                request.addOption("-f", item.formatSelector ?: "bestaudio/best")
            }
            request.addOption("--extract-audio")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
        } else {
            if (!isDirectStream) {
                val defaultSelector = if (isPh) "best/bestvideo+bestaudio" else "bestvideo+bestaudio/best"
                request.addOption("-f", item.formatSelector ?: defaultSelector)
            } else if (isPh && item.qualityHeight > 0) {
                // Pornhub hands us a direct HLS master URL, which can expose several
                // renditions. Without a format filter yt-dlp just grabs the highest
                // one and ignores the quality the user actually picked.
                request.addOption("-f", "best[height<=${item.qualityHeight}]/best")
            }
            request.addOption("--merge-output-format", "mp4")
        }
        return request
    }

    private suspend fun runYtDlpAttempt(
        item: DownloadItem,
        outDir: File,
        request: YoutubeDLRequest,
        onProgress: (TransferProgress) -> Unit,
    ) = coroutineScope {

        // yt-dlp renders progress for its own terminal, and the bundled library
        // only understands one legacy line shape. Watch the output folder instead:
        // byte growth gives reliable percent/speed/ETA even for merged
        // video+audio downloads, where per-file percentages reset halfway.
        val finished = AtomicBoolean(false)
        val totalBytes = java.util.concurrent.atomic.AtomicLong(if (item.fileSize > 0) item.fileSize else 0L)
        val pollJob = launch {
            var lastBytes = 0L
            var lastTs = System.currentTimeMillis()
            var smoothSpeed = 0.0
            var primed = false
            while (isActive && !finished.get()) {
                if (isCancelled(item.id)) break
                delay(450)
                if (isCancelled(item.id)) break
                val bytes = runCatching {
                    outDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                }.getOrDefault(0L)
                val now = System.currentTimeMillis()
                val dt = (now - lastTs).coerceAtLeast(1L)
                val total = totalBytes.get()
                if (!primed) {
                    primed = true
                } else if (bytes > 0L) {
                    val instant = ((bytes - lastBytes).coerceAtLeast(0L) * 1000.0) / dt
                    smoothSpeed = if (smoothSpeed <= 0.0) instant else smoothSpeed * 0.65 + instant * 0.35
                    val percent = if (total > 0L) (bytes.toFloat() / total.toFloat()).coerceIn(0f, 0.99f) else -1f
                    val eta = if (smoothSpeed >= 1024.0 && total > bytes) {
                        ((total - bytes) / smoothSpeed).toLong().coerceAtLeast(0L)
                    } else 0L
                    if (!isCancelled(item.id)) {
                        runCatching {
                            onProgress(
                                TransferProgress(
                                    percent = percent,
                                    etaSeconds = eta,
                                    speedBps = smoothSpeed.toLong(),
                                    downloadedBytes = bytes,
                                    totalBytes = total,
                                )
                            )
                        }
                    }
                }
                lastBytes = bytes
                lastTs = now
            }
        }

        // yt-dlp prints one progress line per stream; merged video+audio is two
        // streams whose totals only become known as each one starts. Track them so
        // the expected size can be corrected from the real bytes (analysis estimates
        // are often 1.5-2x too large for YouTube).
        var streamTotal = 0L
        var finishedBytes = 0L
        var lastLinePercent = -1f
        try {
            YoutubeDL.getInstance().execute(request, item.id) { percent, eta, line ->
                if (isCancelled(item.id)) return@execute
                val parsed = parseProgressLine(line)
                if (BuildConfig.DEBUG && line.contains("[download]", ignoreCase = true)) {
                    android.util.Log.d("YtDlpProgress", "lib=$percent/${eta}s parsed=$parsed | $line")
                }
                if (parsed == null) {
                    if (totalBytes.get() == 0L && percent >= 0f) {
                        val p = (percent / 100f).coerceIn(0f, 1f)
                        val total = item.fileSize
                        if (!isCancelled(item.id)) {
                            onProgress(
                                TransferProgress(
                                    percent = p,
                                    etaSeconds = if (eta > 0) eta else 0L,
                                    speedBps = 0L,
                                    downloadedBytes = if (total > 0) (total * p).toLong() else 0L,
                                    totalBytes = total,
                                )
                            )
                        }
                    }
                    return@execute
                }

                if (parsed.totalBytes > 0L) {
                    val newStream = parsed.totalBytes != streamTotal &&
                        lastLinePercent > 0.9f && parsed.percent < 0.5f
                    if (newStream) finishedBytes += streamTotal
                    streamTotal = parsed.totalBytes
                    if (newStream || totalBytes.get() == 0L) {
                        totalBytes.set((finishedBytes + streamTotal).coerceAtLeast(1L))
                    }
                }
                lastLinePercent = parsed.percent

                // The folder poller owns the percentage once the expected size is
                // known. yt-dlp's line is the fallback for unknown-size media.
                if (totalBytes.get() == 0L && !isCancelled(item.id)) {
                    val total = parsed.totalBytes
                    onProgress(
                        TransferProgress(
                            percent = parsed.percent,
                            etaSeconds = parsed.etaSeconds,
                            speedBps = parsed.speedBps,
                            downloadedBytes = if (total > 0) (total * parsed.percent).toLong() else 0L,
                            totalBytes = total,
                        )
                    )
                }
            }
        } finally {
            finished.set(true)
            pollJob.cancel()
        }
    }

    private val cancelledIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun cancel(itemId: String) {
        cancelledIds.add(itemId)
        runCatching { YoutubeDL.getInstance().destroyProcessById(itemId) }
    }

    fun isCancelled(itemId: String): Boolean = cancelledIds.contains(itemId)
    fun clearCancelled(itemId: String) { cancelledIds.remove(itemId) }

    private val progressPct = Regex("""\[download]\s+(\d+(?:\.\d+)?)%""")
    private val progressEta = Regex("""\bETA\s+(?:(\d+):)?(\d+):(\d{2})\b""")
    private val progressSpeed = Regex("""\bat\s+(\d+(?:\.\d+)?)\s*([KMGTP]?i?B)/s""")
    private val progressTotal = Regex("""\bof\s+~?\s*(\d+(?:\.\d+)?)\s*([KMGTP]?i?B)\b""")

    private data class ProgressSample(
        val percent: Float,
        val etaSeconds: Long,
        val speedBps: Long,
        val totalBytes: Long,
    )

    /**
     * Parses one yt-dlp output line into a progress sample. Returns null when the
     * line carries no download percentage, so informational lines never reset the
     * UI to 0%.
     */
    private fun parseProgressLine(line: String): ProgressSample? {
        val pct = progressPct.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull()
            ?: return null
        val eta = progressEta.find(line)?.let { m ->
            val parts = m.groupValues.drop(1).filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }
            when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> 0L
            }
        } ?: 0L
        val speed = progressSpeed.find(line)?.let { m ->
            parseSize(m.groupValues.getOrNull(1), m.groupValues.getOrNull(2))
        } ?: 0L
        val total = progressTotal.find(line)?.let { m ->
            parseSize(m.groupValues.getOrNull(1), m.groupValues.getOrNull(2))
        } ?: 0L
        return ProgressSample(
            percent = (pct / 100f).coerceIn(0f, 1f),
            etaSeconds = eta,
            speedBps = speed,
            totalBytes = total,
        )
    }

    private fun parseSize(value: String?, unit: String?): Long {
        val amount = value?.toDoubleOrNull() ?: return 0L
        val u = unit.orEmpty().uppercase()
        val base = if (u.contains("I")) 1024.0 else 1000.0
        val multiplier = when (u.firstOrNull()) {
            'K' -> base
            'M' -> base * base
            'G' -> base * base * base
            'T' -> base * base * base * base
            'P' -> base * base * base * base * base
            else -> 1.0
        }
        return (amount * multiplier).toLong()
    }

    private fun fileNameFromDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val utf8 = Regex("""filename\*\s*=\s*UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        if (utf8 != null) {
            return runCatching { java.net.URLDecoder.decode(utf8.trim(), "UTF-8") }
                .getOrDefault(utf8).let { safeFileName(it) }
        }
        val plain = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.getOrNull(1)
        return plain?.let { safeFileName(it.trim()) }
    }

    fun shortError(error: Throwable): String {
        val raw = error.message ?: error.javaClass.simpleName
        val cleaned = raw
            .replace(Regex("""\u001B\[[;\d]*m"""), "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
        val line = cleaned.lastOrNull() ?: "Unknown error"
        return line.take(300)
    }

    fun estimatedSizeLabel(size: Long): String = if (size > 0) "~${humanBytes(size)}" else "size unknown"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"
}
