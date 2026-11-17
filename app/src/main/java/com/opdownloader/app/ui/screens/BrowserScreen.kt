package com.opdownloader.app.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.engine.Net
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.RoundIconButton
import com.opdownloader.app.ui.theme.LocalAppColors
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONTokener

private data class SiteBookmark(val label: String, val url: String)

private val bookmarks = listOf(
    SiteBookmark("YouTube", "https://m.youtube.com"),
    SiteBookmark("Instagram", "https://www.instagram.com"),
    SiteBookmark("TikTok", "https://www.tiktok.com"),
    SiteBookmark("Facebook", "https://m.facebook.com"),
    SiteBookmark("X / Twitter", "https://mobile.x.com"),
    SiteBookmark("Vimeo", "https://vimeo.com"),
    SiteBookmark("SoundCloud", "https://soundcloud.com"),
    SiteBookmark("Dailymotion", "https://www.dailymotion.com"),
    SiteBookmark("Reddit", "https://www.reddit.com"),
    SiteBookmark("Pinterest", "https://www.pinterest.com"),
    SiteBookmark("Twitch", "https://m.twitch.tv"),
    SiteBookmark("Bilibili", "https://m.bilibili.com"),
)

private val mediaExtensions = listOf(
    ".mp4", ".m4v", ".webm", ".mkv", ".mov", ".mp3", ".m4a", ".aac",
    ".flac", ".ogg", ".opus", ".m3u8", ".mpd",
)

/** Anything smaller is almost always a thumbnail, tracking pixel or ad bump. */
private const val MIN_VIDEO_BYTES = 150L * 1024L
private const val MAX_CANDIDATES = 30

/** Quick filters offered in the sniffer sheet. */
private val sizeFilters = longArrayOf(0L, 1_000_000L, 5_000_000L, 20_000_000L)
private val sizeFilterLabels = listOf("All", "> 1 MB", "> 5 MB", "> 20 MB")

private data class SniffedVideo(
    val url: String,
    val title: String? = null,
    val quality: String? = null,
    val mime: String? = null,
    val sizeBytes: Long = 0L,
    val probing: Boolean = false,
) {
    val playlist: Boolean get() = isPlaylistUrl(url)
    val hostLabel: String
        get() = runCatching { android.net.Uri.parse(url).host }.getOrNull()
            ?.removePrefix("www.")
            ?.takeIf { it.isNotBlank() }
            ?: "stream"

    val displayTitle: String
        get() {
            if (!title.isNullOrBlank()) return title
            return sniffName(url)
        }

    val displayQuality: String
        get() {
            if (!quality.isNullOrBlank()) return quality
            extractItagQuality(url)?.let { return it }
            if (isWatchPageUrl(url)) return "Full Video (Auto Best)"
            return sniffType(url, mime)
        }

    val effectiveSize: Long
        get() = if (sizeBytes > 0L) sizeBytes else extractClen(url)
}

private data class ScannedItem(
    val url: String,
    val title: String? = null,
    val quality: String? = null,
    val sizeBytes: Long = 0L,
    val mime: String? = null,
)

private fun safeQueryParam(url: String, key: String): String? = runCatching {
    val uri = android.net.Uri.parse(url)
    if (uri.isHierarchical) uri.getQueryParameter(key) else null
}.getOrNull()

private fun safeHost(url: String): String = runCatching {
    val uri = android.net.Uri.parse(url)
    if (uri.isHierarchical) uri.host?.removePrefix("www.").orEmpty() else ""
}.getOrDefault("")

private fun safeLastPathSegment(url: String): String = runCatching {
    val uri = android.net.Uri.parse(url)
    if (uri.isHierarchical) uri.lastPathSegment.orEmpty() else ""
}.getOrDefault("")

private fun extractClen(url: String): Long = runCatching {
    safeQueryParam(url, "clen")?.toLongOrNull()
}.getOrNull() ?: 0L

private fun itagToQuality(itag: String?): String {
    return when (itag) {
        "18" -> "360p (MP4)"
        "22" -> "720p HD (MP4)"
        "37" -> "1080p Full HD (MP4)"
        "38" -> "4K UHD (MP4)"
        "137" -> "1080p Full HD"
        "136" -> "720p HD"
        "135" -> "480p"
        "134" -> "360p"
        "133" -> "240p"
        "160" -> "144p"
        "248" -> "1080p Full HD"
        "247" -> "720p HD"
        "244" -> "480p"
        "243" -> "360p"
        "242" -> "240p"
        "278" -> "144p"
        "271" -> "1440p 2K"
        "313" -> "2160p 4K"
        "140" -> "Audio (128 kbps M4A)"
        "139" -> "Audio (48 kbps)"
        "249" -> "Audio (Opus 50k)"
        "250" -> "Audio (Opus 70k)"
        "251" -> "Audio (Opus 160k)"
        else -> if (itag != null) "Quality (itag $itag)" else "Video Stream"
    }
}

private fun extractItagQuality(url: String): String? = runCatching {
    safeQueryParam(url, "itag")?.let { itagToQuality(it) }
}.getOrNull()

private fun extractVideoId(url: String): String? = runCatching {
    val v = safeQueryParam(url, "v")
    if (!v.isNullOrBlank()) return@runCatching v
    if (url.contains("/shorts/") || url.contains("youtu.be/")) {
        val segment = safeLastPathSegment(url)
        if (segment.isNotBlank()) return@runCatching segment
    }
    null
}.getOrNull()

private fun isCurrentOrDirectMedia(url: String, currentUrl: String?): Boolean {
    if (isWatchPageUrl(url)) {
        val currentVideoId = currentUrl?.let { extractVideoId(it) }
        val thisVideoId = extractVideoId(url)
        if (currentVideoId != null && thisVideoId != null && currentVideoId != thisVideoId) {
            return false
        }
    }
    return true
}

private const val SNIFFER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

private val sniffUserAgent: String
    get() = SNIFFER_USER_AGENT

private fun isPlaylistUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".m3u8") || path.endsWith(".mpd")
}

private fun isStreamingUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("videoplayback") ||
        lower.contains("googlevideo.com") ||
        lower.contains(".m3u8") ||
        lower.contains(".mpd")
}

private fun isWatchPageUrl(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.contains("youtube.com/watch") || lower.contains("youtu.be/") || lower.contains("youtube.com/shorts/")) return true
    if (lower.contains("instagram.com/p/") || lower.contains("instagram.com/reel/")) return true
    if (lower.contains("tiktok.com/@") && lower.contains("/video/")) return true
    if (lower.contains("twitter.com/") && lower.contains("/status/")) return true
    if (lower.contains("x.com/") && lower.contains("/status/")) return true
    if (lower.contains("vimeo.com/") && url.filter { it.isDigit() }.length >= 6) return true
    if (lower.contains("dailymotion.com/video/")) return true
    if (lower.contains("pornhub.com/view_video.php") || lower.contains("pornhub.com/embed/") ||
        lower.contains("phub.to/") || lower.contains("thumbzilla.com/video/") ||
        lower.contains("xvideos.com/video")) return true
    return false
}

/** HLS/DASH fragments are noise in the picker; the manifest is the useful URL. */
private fun isSegmentUrl(url: String): Boolean {
    val path = url.substringBefore('?').lowercase()
    return path.endsWith(".ts") || path.endsWith(".m4s") ||
        path.endsWith(".cmfv") || path.endsWith(".cmfa")
}

private fun looksLikeMedia(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.startsWith("data:") || lower.startsWith("blob:") || lower.startsWith("javascript:") || lower.startsWith("about:")) return false
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    if (isSegmentUrl(lower)) return false
    if (isStreamingUrl(lower)) return true
    if (isWatchPageUrl(lower)) return true
    val path = lower.substringBefore('?')
    if (mediaExtensions.any { path.endsWith(it) || lower.contains(it) }) return true
    if (lower.contains("mime=video") || lower.contains("mime=audio") ||
        lower.contains("mime%3dvideo") || lower.contains("mime%3daudio") ||
        lower.contains("format=mp4") || lower.contains("ext=mp4")) return true
    return false
}

/**
 * YouTube serves the same video as many byte-range URLs. Collapse them so a
 * picker is not drowned in duplicate segments; the first one stays usable.
 */
private fun candidateKey(url: String): String = runCatching {
    if (isWatchPageUrl(url)) {
        val v = safeQueryParam(url, "v") ?: safeLastPathSegment(url).takeIf { it.isNotBlank() }
        if (!v.isNullOrBlank()) return "yt:watch:$v"
        return url.substringBefore('?')
    }
    if (url.contains("videoplayback")) {
        val id = safeQueryParam(url, "id") ?: safeQueryParam(url, "docid")
        val itag = safeQueryParam(url, "itag").orEmpty()
        if (!id.isNullOrBlank()) return "gv:$id:$itag"
    }
    url.substringBefore('#')
}.getOrDefault(url.substringBefore('#'))

/**
 * Chromium reports the document URL as the title of untitled pages, which made
 * plain <video> rows show the page address instead of the file name. Treat any
 * URL-shaped title as "no title" so [sniffName] can label the stream.
 */
private fun cleanCandidateTitle(title: String?, pageUrl: String?): String? {
    val t = title?.trim().orEmpty()
    if (t.isEmpty()) return null
    if (t.startsWith("http://", true) || t.startsWith("https://", true)) return null
    val page = pageUrl?.trim().orEmpty()
    if (page.isNotEmpty()) {
        val bare = page.removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (t == page || t.trimEnd('/') == bare) return null
    }
    return t
}

private fun sniffName(url: String): String = runCatching {
    if (isWatchPageUrl(url)) {
        val host = safeHost(url)
        val v = safeQueryParam(url, "v")
        return when {
            host.contains("youtube") && !v.isNullOrBlank() -> "YouTube Video ($v)"
            host.isNotBlank() -> "$host video"
            else -> "Web Video"
        }
    }
    val raw = safeLastPathSegment(url)
    val act = raw.lowercase()
    if (act == "videoplayback" || act == "playlist" || act == "master" || act == "manifest" || act.isBlank()) {
        val itag = safeQueryParam(url, "itag")
        val q = itagToQuality(itag)
        val host = safeHost(url)
        return when {
            itag != null -> "Video Stream ($q)"
            host.isNotBlank() -> "$host stream"
            else -> "Video stream"
        }
    }
    val decoded = runCatching { android.net.Uri.decode(raw) }.getOrDefault(raw)
    return if (decoded.length > 46) decoded.take(43) + "…" else decoded
}.getOrDefault("Video stream")

private fun sniffType(url: String, mime: String?): String {
    val type = mime?.substringBefore(';')?.trim()?.lowercase()
    if (!type.isNullOrBlank() && type.contains('/') && type != "video" && type != "audio") {
        return type.substringAfter('/').uppercase()
    }
    if (isPlaylistUrl(url)) return "STREAM"
    if (url.contains("videoplayback") || url.contains("googlevideo.com")) return "YOUTUBE"
    if (isWatchPageUrl(url)) return "WEB VIDEO"
    val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4" -> "MP4"
        "m4v" -> "M4V"
        "webm" -> "WEBM"
        "mkv" -> "MKV"
        "mov" -> "MOV"
        "mp3" -> "MP3"
        "m4a" -> "M4A"
        "aac" -> "AAC"
        "flac" -> "FLAC"
        "ogg" -> "OGG"
        "opus" -> "OPUS"
        "m3u8", "mpd" -> "STREAM"
        else -> "VIDEO"
    }
}

private fun filterVideos(list: List<SniffedVideo>, filterIndex: Int): List<SniffedVideo> {
    val min = maxOf(MIN_VIDEO_BYTES, sizeFilters.getOrElse(filterIndex) { 0L })
    return list
        .filter { video ->
            val size = video.effectiveSize
            if (size > 0L) return@filter size >= min
            // Watch pages and adaptive streams carry no byte size but are still
            // the useful "download the whole thing" entries.
            filterIndex == 0 || isWatchPageUrl(video.url) || video.playlist || isStreamingUrl(video.url)
        }
        .sortedWith(
            compareByDescending<SniffedVideo> { isWatchPageUrl(it.url) }
                .thenByDescending { it.effectiveSize }
                .thenBy { it.displayTitle },
        )
}

private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.contains(" ") || !trimmed.contains(".")) {
        return "https://www.google.com/search?q=" + android.net.Uri.encode(trimmed)
    }
    return "https://$trimmed"
}

private fun parseScannedItems(raw: String?): List<ScannedItem> {
    if (raw.isNullOrBlank() || raw == "null") return emptyList()
    val decoded = runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull() ?: raw
    val array = runCatching { JSONArray(decoded) }.getOrNull() ?: return emptyList()
    val out = ArrayList<ScannedItem>(array.length())
    for (index in 0 until array.length()) {
        val obj = array.optJSONObject(index)
        if (obj != null) {
            val url = obj.optString("url").trim()
            if (url.isNotEmpty()) {
                out += ScannedItem(
                    url = url,
                    title = obj.optString("title").takeIf { it.isNotBlank() },
                    quality = obj.optString("quality").takeIf { it.isNotBlank() },
                    sizeBytes = obj.optLong("size", 0L),
                    mime = obj.optString("mime").takeIf { it.isNotBlank() },
                )
            }
        } else {
            val urlStr = array.optString(index).trim()
            if (urlStr.isNotEmpty()) {
                out += ScannedItem(url = urlStr)
            }
        }
    }
    return out
}

private fun isMediaMime(type: String?): Boolean {
    if (type.isNullOrBlank()) return false
    val t = type.lowercase()
    return t.startsWith("video/") ||
        t.startsWith("audio/") ||
        t.contains("mpegurl") ||
        t.contains("dash") ||
        t == "application/octet-stream"
}

/** Server-side size lookup, with a one-byte ranged GET as fallback for HEAD-hostile CDNs. */
private suspend fun probeMedia(url: String, referer: String?): Pair<Long, String?> = withContext(Dispatchers.IO) {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
        return@withContext 0L to null
    }
    val cookie = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull().orEmpty()

    fun builder(): Request.Builder = Request.Builder()
        .url(url)
        .header("User-Agent", sniffUserAgent)
        .header("Accept", "*/*")
        .apply {
            if (!referer.isNullOrBlank()) header("Referer", referer)
            if (cookie.isNotBlank()) header("Cookie", cookie)
        }

    try {
        // Step 1: HEAD probe
        Net.probeClient.newCall(builder().head().build()).execute().use { response ->
            if (com.opdownloader.app.BuildConfig.DEBUG) {
                android.util.Log.d("OP_PROBE", "head $url -> ${response.code} len=${response.header("Content-Length")} type=${response.header("Content-Type")}")
            }
            if (response.isSuccessful) {
                val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                val length = response.header("Content-Length")?.toLongOrNull() ?: 0L
                if (isMediaMime(type) && length > 0L) {
                    return@withContext (length to type)
                }
            }
        }

        // Step 2: 1-byte Range GET fallback
        Net.probeClient.newCall(builder().header("Range", "bytes=0-1").build()).execute().use { response ->
            if (response.isSuccessful) {
                val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                val total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
                    ?: response.header("Content-Length")?.toLongOrNull()
                    ?: 0L
                val validMedia = isMediaMime(type) || (type != null && !type.startsWith("text/html") && !type.startsWith("image/") && !type.startsWith("application/json") && !type.startsWith("text/css"))
                val size = if (validMedia) total else 0L
                return@withContext (size to type)
            }
        }
        0L to null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Throwable) {
        if (com.opdownloader.app.BuildConfig.DEBUG) {
            android.util.Log.d("OP_PROBE", "fail $url ${e.javaClass.simpleName}: ${e.message}")
        }
        0L to null
    }
}

/** Runs inside the page: collects real player sources plus media resource entries. */
private const val SCAN_PAGE_JS = """
(function(){
  var results = [];
  var seen = {};

  var pageTitle = '';
  try {
    if (window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.videoDetails && window.ytInitialPlayerResponse.videoDetails.title) {
      pageTitle = window.ytInitialPlayerResponse.videoDetails.title;
    } else {
      var og = document.querySelector('meta[property="og:title"]');
      pageTitle = (og && og.content) || document.title || '';
    }
    pageTitle = pageTitle.replace(/\s*-\s*YouTube\s*${'$'}/i, '').trim();
  } catch(e){}

  function addItem(u, title, quality, size, mime, itag) {
    if (!u || typeof u !== 'string') return;
    var trimmed = u.trim();
    if (trimmed.length === 0 || trimmed.indexOf('data:') === 0 || trimmed.indexOf('blob:') === 0) return;
    if (seen[trimmed]) return;
    seen[trimmed] = true;
    results.push({
      url: trimmed,
      title: title || pageTitle || '',
      quality: quality || '',
      size: size || 0,
      mime: mime || '',
      itag: itag || ''
    });
  }

  var mediaRegex = /\.(mp4|m4v|webm|mkv|mov|mp3|m4a|aac|flac|ogg|opus|m3u8|mpd)(${'$'}|\?|#)/i;

  try {
    if (window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.streamingData) {
      var sd = window.ytInitialPlayerResponse.streamingData;
      var processFormat = function(f) {
        if (!f) return;
        var u = f.url;
        if (!u && f.signatureCipher) {
          try {
            var params = new URLSearchParams(f.signatureCipher);
            u = params.get('url');
          } catch(e){}
        }
        if (u) {
          var q = f.qualityLabel || (f.height ? (f.height + 'p') : '') || (f.audioQuality ? 'Audio' : '');
          var sz = parseInt(f.contentLength || '0', 10);
          addItem(u, pageTitle, q, sz, f.mimeType || '', f.itag ? String(f.itag) : '');
        }
      };
      if (sd.formats) sd.formats.forEach(processFormat);
      if (sd.adaptiveFormats) sd.adaptiveFormats.forEach(processFormat);
    }
  } catch(e){}

  try {
    if (window.location && window.location.href) {
      var h = window.location.href;
      if (h.indexOf('/watch') !== -1 || h.indexOf('/shorts/') !== -1) {
        addItem(h, pageTitle, 'Full Video (Auto Best)', 0, 'video/web', '');
      }
    }
  } catch(e){}

  try {
    document.querySelectorAll('video,audio').forEach(function(m){
      var src = m.currentSrc || m.src;
      var q = (m.videoHeight ? (m.videoHeight + 'p') : '') || '';
      if (src) addItem(src, pageTitle, q, 0, '', '');
      m.querySelectorAll('source').forEach(function(x){
        if (x.src) addItem(x.src, pageTitle, q, 0, x.type || '', '');
      });
    });
  } catch(e){}

  try {
    performance.getEntriesByType('resource').forEach(function(e){
      if (e.initiatorType === 'video' || e.initiatorType === 'audio' || mediaRegex.test(e.name) ||
          e.name.indexOf('videoplayback') !== -1 || e.name.indexOf('googlevideo.com') !== -1) {
        addItem(e.name, pageTitle, '', 0, '', '');
      }
    });
  } catch(e){}

  try {
    document.querySelectorAll('a[href]').forEach(function(a){
      if (mediaRegex.test(a.href)) addItem(a.href, a.innerText || pageTitle, '', 0, '', '');
    });
  } catch(e){}

  return JSON.stringify(results.slice(0, 50));
})()
"""

private class SnifferJsBridge(
    private val onPageInfo: (url: String, title: String) -> Unit,
) {
    @android.webkit.JavascriptInterface
    fun updatePage(url: String, title: String) {
        onPageInfo(url, title)
    }
}

private const val SYNC_PAGE_JS = """
(function(){
  function sync(){
    try{
      var u = window.location.href;
      var t = '';
      if(window.ytInitialPlayerResponse && window.ytInitialPlayerResponse.videoDetails && window.ytInitialPlayerResponse.videoDetails.title){
        t = window.ytInitialPlayerResponse.videoDetails.title;
      }
      if(!t){
        var h1 = document.querySelector('h1.title, h2.title, .slim-video-metadata-title, .slim-video-information-title, .media-item-headline');
        if(h1) t = h1.innerText || '';
      }
      if(!t){
        var og = document.querySelector('meta[property="og:title"]');
        if(og) t = og.content || '';
      }
      if(!t) t = document.title || '';
      if(window.SnifferBridge && window.SnifferBridge.updatePage){
        window.SnifferBridge.updatePage(u, t);
      }
    }catch(e){}
  }
  sync();
  if(!window.__opSyncInterval){
    window.__opSyncInterval = setInterval(sync, 1000);
    window.addEventListener('popstate', sync);
  }
})()
"""

/**
 * Player-agnostic ad skipping: clicks visible Skip/Close controls, and
 * fast-forwards HTML5 ad reels (YouTube-style) while restoring mute state.
 */
private const val AD_SKIP_JS = """
(function(){
  if(window.__opSkipAdsOn) return;
  window.__opSkipAdsOn=true;
  var mutedMap = new WeakMap();
  var rateMap = new WeakMap();

  function visible(el){
    try{
      if(!el||!el.getBoundingClientRect) return false;
      var r=el.getBoundingClientRect();
      if(r.width<4||r.height<4) return false;
      var s=getComputedStyle(el);
      return s.visibility!=='hidden'&&s.display!=='none'&&s.opacity!=='0';
    }catch(e){ return false; }
  }

  function triggerClick(el){
    try{ el.click(); }catch(e){}
    try{
      var ev = new MouseEvent('click', { bubbles: true, cancelable: true, view: window });
      el.dispatchEvent(ev);
    }catch(e){}
  }

  function label(el){
    try{
      return ((el.innerText||el.textContent||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.title||''))
        .replace(/\s+/g,' ').trim();
    }catch(e){ return ''; }
  }

  var skipSelectors = [
    '.ytp-ad-skip-button',
    '.ytp-ad-skip-button-modern',
    '.ytp-skip-ad-button',
    '.ytp-ad-skip-button-container button',
    '.ytp-ad-skip-button-slot button',
    '.ytp-ad-overlay-close-button',
    '.ytm-ad-overlay-close-button',
    '.videoAdUiSkipButton',
    '.videoAdUiAction',
    '.videoAdUiSkipContainer button',
    '.jw-skip-ad',
    '.jw-ad-skip',
    '.jw-ad-skippable',
    '.dmp_SkipButton',
    '.vjs-skip-button',
    '.vjs-ad-skip-button',
    '[class*="skip-ad" i]',
    '[class*="skipAd" i]',
    '[id*="skip-ad" i]',
    '[class*="ad-overlay-close" i]',
    '[class*="ad-close" i]',
    '[class*="adClose" i]',
    '[aria-label*="skip ad" i]',
    '[aria-label*="close ad" i]'
  ].join(',');

  function isAdVideo(v){
    try{
      // 1. YouTube player check (strict - only when player container explicitly indicates an active ad)
      var ytPlayer = v.closest('#movie_player, .html5-video-player');
      if(ytPlayer){
        if(ytPlayer.classList.contains('ad-showing') || ytPlayer.classList.contains('ad-interrupting')) return true;
        var ytOverlay = ytPlayer.querySelector('.ytp-ad-player-overlay, .ytp-ad-player-overlay-skip-or-preview');
        if(ytOverlay && visible(ytOverlay)) return true;
        return false;
      }
      // 2. JWPlayer
      var jw = v.closest('.jwplayer');
      if(jw && (jw.classList.contains('jw-flag-ads') || jw.classList.contains('jw-ad-playing'))) return true;
      // 3. Video.js
      var vjs = v.closest('.video-js');
      if(vjs && vjs.classList.contains('vjs-ad-playing')) return true;
      // 4. IMA container
      var ima = v.closest('.ima-ad-container');
      if(ima && visible(ima)) return true;
      // 5. Generic ad-mode containers used by other players
      var generic = v.closest('[class*="ad-showing" i],[class*="ad-playing" i],[class*="ad-interrupting" i]');
      if(generic && visible(generic)) return true;
    }catch(e){}
    return false;
  }

  function skip(){
    // 1. Click dedicated ad skip buttons
    try{
      document.querySelectorAll(skipSelectors).forEach(function(el){
        if(visible(el)){ triggerClick(el); }
      });
    }catch(e){}

    // 2. Click buttons with explicit "skip ad" text (never plain "skip" or "next")
    try{
      document.querySelectorAll('button,[role="button"]').forEach(function(el){
        if(!visible(el)) return;
        var t=label(el);
        if(/^(skip( ad| ads| advertisement)|skip in \d+s?|close( ad| ads| advertisement)|saltar anuncio|passer l'annonce|überspringen)$/i.test(t)){
          triggerClick(el);
        }
      });
    }catch(e){}

    // 3. For videos actively inside an ad container: speed up & mute during ad, restore when done.
    // NEVER touch currentTime so we never skip user videos or trigger playlist autoplay!
    try{
      document.querySelectorAll('video').forEach(function(v){
        try{
          var isAd = isAdVideo(v);
          if(isAd){
            if(!mutedMap.has(v)) mutedMap.set(v, v.muted);
            if(!rateMap.has(v)) rateMap.set(v, v.playbackRate || 1.0);
            v.muted = true;
            if(v.playbackRate < 16.0) v.playbackRate = 16.0;
          } else {
            if(mutedMap.has(v)){
              v.muted = mutedMap.get(v);
              mutedMap.delete(v);
            }
            if(rateMap.has(v)){
              v.playbackRate = rateMap.get(v);
              rateMap.delete(v);
            }
          }
        }catch(e){}
      });
    }catch(e){}
  }

  var pending=false;
  function schedule(){ if(pending) return; pending=true; setTimeout(function(){ pending=false; skip(); },150); }
  if(window.__opSkipAdsTimer) clearInterval(window.__opSkipAdsTimer);
  window.__opSkipAdsTimer=setInterval(skip,800);
  if(window.__opSkipAdsObserver) window.__opSkipAdsObserver.disconnect();
  window.__opSkipAdsObserver=new MutationObserver(schedule);
  try{ window.__opSkipAdsObserver.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['class','style']}); }catch(e){}
  skip();
})()
"""

private const val AD_SKIP_STOP_JS = """
(function(){
  window.__opSkipAdsOn=false;
  if(window.__opSkipAdsTimer){ clearInterval(window.__opSkipAdsTimer); window.__opSkipAdsTimer=null; }
  if(window.__opSkipAdsObserver){ window.__opSkipAdsObserver.disconnect(); window.__opSkipAdsObserver=null; }
  try{
    document.querySelectorAll('video').forEach(function(v){
      v.playbackRate = 1.0;
    });
  }catch(e){}
})()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    nav: NavHostController,
    initialUrl: String? = null,
    onFullscreenChange: (Boolean) -> Unit = {},
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val main = remember { Handler(Looper.getMainLooper()) }
    val scope = rememberCoroutineScope()
    val probeGate = remember { Semaphore(3) }

    var address by rememberSaveable(initialUrl) { mutableStateOf(initialUrl.orEmpty()) }
    var isAddressFocused by remember { mutableStateOf(false) }
    var currentUrl by rememberSaveable(initialUrl) { mutableStateOf<String?>(initialUrl) }
    var currentPageTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var videos by remember { mutableStateOf<List<SniffedVideo>>(emptyList()) }
    var showVideos by rememberSaveable { mutableStateOf(false) }
    var adSkip by rememberSaveable { mutableStateOf(true) }
    var sizeFilter by rememberSaveable { mutableIntStateOf(0) }
    var fullscreen by remember { mutableStateOf(false) }
    val known = remember { mutableSetOf<String>() }
    val webRef = remember { mutableStateOf<WebView?>(null) }

    fun setFullscreen(value: Boolean) {
        fullscreen = value
        onFullscreenChange(value)
    }

    // Never leave the tab bar hidden if this screen is removed while fullscreen.
    DisposableEffect(Unit) {
        onDispose { onFullscreenChange(false) }
    }

    // The floating tab bar is navInset + 96dp tall. Keep both the browser and
    // its floating sniffer fully above it on gesture and 3-button navigation.
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeInset = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val aboveTabs = maxOf(imeInset, navInset + 90.dp)

    val visibleVideos = remember(videos, sizeFilter) { filterVideos(videos, sizeFilter) }
    val scanning = videos.any { it.probing }

    fun clearVideos() {
        videos = emptyList()
        known.clear()
        sizeFilter = 0
    }

    fun sniff(
        raw: String,
        hint: String? = null,
        title: String? = null,
        quality: String? = null,
        sizeBytes: Long = 0L,
    ) {
        val url = raw.trim()
        if (url.isBlank()) return
        if (url.startsWith("data:") || url.startsWith("blob:") || url.startsWith("javascript:") || url.startsWith("about:")) return
        if (isSegmentUrl(url)) return
        if (hint == null && !looksLikeMedia(url) && !isWatchPageUrl(url)) return
        if (!isCurrentOrDirectMedia(url, currentUrl)) return

        main.post {
            runCatching {
                val key = candidateKey(url)
                val initialSize = if (sizeBytes > 0L) sizeBytes else extractClen(url)
                val initialQuality = quality ?: extractItagQuality(url)
                val initialTitle = cleanCandidateTitle(title, currentUrl)
                    ?: cleanCandidateTitle(currentPageTitle, currentUrl)

                val existing = videos.find { candidateKey(it.url) == key }
                if (existing != null) {
                    videos = videos.map { v ->
                        if (candidateKey(v.url) == key) {
                            v.copy(
                                title = cleanCandidateTitle(v.title, currentUrl)
                                    ?.takeIf { !it.startsWith("YouTube Video") && !it.startsWith("Video stream") }
                                    ?: initialTitle,
                                quality = v.quality ?: initialQuality,
                                sizeBytes = if (v.sizeBytes > 0L) v.sizeBytes else initialSize,
                            )
                        } else v
                    }
                    return@runCatching
                }

                if (!known.add(key)) return@runCatching
                val isAdaptive = isPlaylistUrl(url) || isStreamingUrl(url) || isWatchPageUrl(url)
                val newVideo = SniffedVideo(
                    url = url,
                    title = initialTitle,
                    quality = initialQuality,
                    mime = hint,
                    sizeBytes = initialSize,
                    probing = !isAdaptive && initialSize == 0L,
                )
                videos = (listOf(newVideo) + videos).take(MAX_CANDIDATES)
                if (isAdaptive || initialSize > 0L) return@runCatching
                val referer = currentUrl
                scope.launch {
                    var meta = probeGate.withPermit { probeMedia(url, referer) }
                    // Busy CDNs sometimes drop the first probe; retry once
                    // before leaving the row as "size unknown".
                    if (meta.first <= 0L) {
                        delay(1200)
                        meta = probeGate.withPermit { probeMedia(url, referer) }
                    }
                    videos = videos.map { v ->
                        if (v.url == url) {
                            v.copy(
                                sizeBytes = if (v.sizeBytes > 0L) v.sizeBytes else meta.first,
                                mime = meta.second ?: v.mime,
                                probing = false,
                            )
                        } else v
                    }
                }
            }
        }
    }

    fun scanPage() {
        val view = webRef.value ?: return
        val liveUrl = view.url
        val liveTitle = view.title?.replace(Regex("\\s*-\\s*YouTube\\s*$", RegexOption.IGNORE_CASE), "")?.trim()
        if (!liveUrl.isNullOrBlank() && liveUrl != currentUrl) {
            currentUrl = liveUrl
            if (!isAddressFocused) address = liveUrl
        }
        if (!liveTitle.isNullOrBlank()) {
            currentPageTitle = liveTitle
        }
        view.evaluateJavascript(SYNC_PAGE_JS, null)
        currentUrl?.let {
            if (isWatchPageUrl(it)) {
                sniff(it, hint = "video/web", title = currentPageTitle, quality = "Full Video (All Qualities)")
            }
        }
        runCatching {
            view.evaluateJavascript(SCAN_PAGE_JS) { result ->
                parseScannedItems(result).forEach { item ->
                    sniff(
                        raw = item.url,
                        hint = item.mime ?: "video/",
                        title = item.title ?: currentPageTitle,
                        quality = item.quality,
                        sizeBytes = item.sizeBytes,
                    )
                }
            }
        }
    }

    fun go(target: String) {
        val normalized = normalizeUrl(target)
        if (normalized.isBlank()) return
        address = normalized
        currentUrl = normalized
        keyboard?.hide()
        clearVideos()
        webRef.value?.loadUrl(normalized)
    }

    DisposableEffect(Unit) {
        onDispose { webRef.value?.destroy() }
    }

    // Keep scanning and ad-skip alive across SPA navigation. Both scripts are
    // idempotent, so re-injection is cheap and survives page swaps.
    LaunchedEffect(currentUrl, adSkip) {
        if (currentUrl == null) return@LaunchedEffect
        if (adSkip) {
            webRef.value?.let { view -> runCatching { view.evaluateJavascript(AD_SKIP_JS, null) } }
        } else {
            webRef.value?.let { view -> runCatching { view.evaluateJavascript(AD_SKIP_STOP_JS, null) } }
        }
        while (true) {
            delay(1500)
            val view = webRef.value ?: continue
            val liveUrl = view.url
            val liveTitle = view.title?.replace(Regex("\\s*-\\s*YouTube\\s*$", RegexOption.IGNORE_CASE), "")?.trim()
            if (!liveUrl.isNullOrBlank() && liveUrl != currentUrl) {
                currentUrl = liveUrl
                if (!isAddressFocused) address = liveUrl
            }
            if (!liveTitle.isNullOrBlank() && liveTitle != currentPageTitle) {
                currentPageTitle = liveTitle
                videos = videos.map {
                    if (it.title.isNullOrBlank() || it.title.startsWith("YouTube Video") || it.title.startsWith("Video stream")) {
                        it.copy(title = liveTitle)
                    } else it
                }
            }
            if (adSkip) runCatching { view.evaluateJavascript(AD_SKIP_JS, null) }
            runCatching { view.evaluateJavascript(SYNC_PAGE_JS, null) }
            runCatching {
                view.evaluateJavascript(SCAN_PAGE_JS) { result ->
                    parseScannedItems(result).forEach { item ->
                        sniff(
                            raw = item.url,
                            hint = item.mime ?: "video/",
                            title = item.title ?: currentPageTitle,
                            quality = item.quality,
                            sizeBytes = item.sizeBytes,
                        )
                    }
                }
            }
        }
    }

    BackHandler(enabled = currentUrl != null) {
        val web = webRef.value
        if (web != null && web.canGoBack()) {
            web.goBack()
        } else {
            currentUrl = null
            address = ""
            showVideos = false
            clearVideos()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            if (!fullscreen) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                AppHeader(
                    title = "Web Sniffer",
                    subtitle = when {
                        currentUrl == null -> "Open a site, grab the stream"
                        scanning -> "Checking detected media sizes…"
                        else -> null // hide the subtitle while browsing to give the page more height
                    },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (currentUrl != null) {
                                RoundIconButton(
                                    icon = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen browser",
                                    onClick = { setFullscreen(true) },
                                )
                                RoundIconButton(
                                    icon = Icons.Default.ArrowBack,
                                    contentDescription = "Previous page",
                                    onClick = {
                                        val web = webRef.value
                                        if (web != null && web.canGoBack()) {
                                            web.goBack()
                                        } else {
                                            currentUrl = null
                                            address = ""
                                            showVideos = false
                                            clearVideos()
                                        }
                                    },
                                )
                                RoundIconButton(
                                    icon = Icons.Default.Refresh,
                                    contentDescription = "Reload",
                                    onClick = { webRef.value?.reload() },
                                )
                            }
                            RoundIconButton(
                                icon = Icons.Default.Home,
                                contentDescription = "Home",
                                onClick = {
                                    currentUrl = null
                                    address = ""
                                    showVideos = false
                                    clearVideos()
                                },
                            )
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (address.isEmpty()) {
                            Text(
                                "Search or enter a website",
                                color = colors.textMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = address,
                            onValueChange = { address = it },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = {
                                isAddressFocused = false
                                keyboard?.hide()
                                go(address)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isAddressFocused = it.isFocused },
                        )
                    }
                    if (address.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceVariant)
                                .clickable {
                                    address = ""
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.primaryDeep)
                            .clickable {
                                isAddressFocused = false
                                keyboard?.hide()
                                go(address)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Go", color = colors.onPrimaryDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            }

            if (currentUrl == null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = if (fullscreen) 0.dp else aboveTabs),
                ) {
                    CardSurface(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(colors.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Public, contentDescription = null, tint = colors.primary, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Standard web browser with saved logins & cookies. Play a video or open a post, then tap the floating sniffer to pick a stream.",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "QUICK OPEN",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        gridItems(bookmarks) { site ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.35f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surfaceElevated)
                                    .clickable { go(site.url) }
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    site.label.take(1),
                                    color = colors.primary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    site.label,
                                    color = colors.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                    CardSurface {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Some sites (YouTube, Instagram) block in-app browsers. Use the analyzer on Home for those — it supports 1000+ sites through yt-dlp.",
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = if (fullscreen) 0.dp else aboveTabs),
                ) {
                    val containerWidth = constraints.maxWidth.toFloat()
                    val containerHeight = constraints.maxHeight.toFloat()
                    val density = LocalDensity.current
                    val marginPx = remember(density) { with(density) { 12.dp.toPx() } }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.surfaceElevated),
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    val webView = this
                                    // Regular browser mode: persist cookies, third-party cookies, and storage
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(webView, true)
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                                    settings.saveFormData = true
                                    settings.mediaPlaybackRequiresUserGesture = false
                                    settings.loadsImagesAutomatically = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.setSupportMultipleWindows(false)
                                    settings.javaScriptCanOpenWindowsAutomatically = false
                                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    setBackgroundColor(colors.surfaceVariant.toArgb())

                                    addJavascriptInterface(
                                        SnifferJsBridge { liveUrl, liveTitle ->
                                            main.post {
                                                val cleanTitle = liveTitle.replace(Regex("\\s*-\\s*YouTube\\s*$", RegexOption.IGNORE_CASE), "").trim()
                                                if (cleanTitle.isNotBlank()) {
                                                    currentPageTitle = cleanTitle
                                                    videos = videos.map {
                                                        if (it.title.isNullOrBlank() || it.title.startsWith("YouTube Video") || it.title.startsWith("Video stream")) {
                                                            it.copy(title = cleanTitle)
                                                        } else it
                                                    }
                                                }
                                                if (liveUrl.isNotBlank()) {
                                                    if (isWatchPageUrl(liveUrl) && isWatchPageUrl(currentUrl.orEmpty())) {
                                                        val oldId = extractVideoId(currentUrl.orEmpty())
                                                        val newId = extractVideoId(liveUrl)
                                                        if (oldId != null && newId != null && oldId != newId) {
                                                            clearVideos()
                                                        }
                                                    }
                                                    currentUrl = liveUrl
                                                    if (!isAddressFocused) address = liveUrl
                                                    if (isWatchPageUrl(liveUrl)) {
                                                        sniff(
                                                            raw = liveUrl,
                                                            hint = "video/web",
                                                            title = cleanTitle.ifBlank { null },
                                                            quality = "Full Video (All Qualities)",
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        "SnifferBridge",
                                    )

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            url?.let {
                                                main.post {
                                                    if (currentUrl != null && it != currentUrl) {
                                                        clearVideos()
                                                    }
                                                    currentUrl = it
                                                    if (!isAddressFocused) address = it
                                                    progress = 0.08f
                                                    if (isWatchPageUrl(it)) {
                                                        sniff(it, hint = "video/web", title = currentPageTitle)
                                                    }
                                                }
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            val clean = view?.title?.replace(Regex("\\s*-\\s*YouTube\\s*$", RegexOption.IGNORE_CASE), "")?.trim()
                                            if (!clean.isNullOrBlank()) {
                                                currentPageTitle = clean
                                                videos = videos.map { if (it.title.isNullOrBlank()) it.copy(title = clean) else it }
                                            }
                                            url?.let {
                                                main.post {
                                                    currentUrl = it
                                                    if (!isAddressFocused) address = it
                                                    progress = 1f
                                                    if (isWatchPageUrl(it)) {
                                                        sniff(it, hint = "video/web", title = currentPageTitle)
                                                    }
                                                }
                                            }
                                            if (adSkip) {
                                                runCatching { view?.evaluateJavascript(AD_SKIP_JS, null) }
                                            }
                                            scanPage()
                                        }

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            val lower = url.lowercase()
                                            if (!lower.startsWith("http://") && !lower.startsWith("https://") && !lower.startsWith("about:") && !lower.startsWith("javascript:")) {
                                                runCatching {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                                    view?.context?.startActivity(intent)
                                                }
                                                return true
                                            }
                                            if (isWatchPageUrl(url)) {
                                                sniff(url, hint = "video/web")
                                                return false
                                            }
                                            val path = url.substringBefore('?').lowercase()
                                            val isDirectMediaFile = path.endsWith(".mp4") || path.endsWith(".mkv") ||
                                                path.endsWith(".webm") || path.endsWith(".avi") || path.endsWith(".mov")
                                            if (isDirectMediaFile) {
                                                sniff(url)
                                                return true
                                            }
                                            return false
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                        ): WebResourceResponse? {
                                            request?.let { req ->
                                                val url = req.url?.toString().orEmpty()
                                                if (url.isNotBlank()) {
                                                    val lower = url.lowercase()
                                                    val headers = runCatching { req.requestHeaders }.getOrNull() ?: emptyMap()
                                                    val destination = headers["Sec-Fetch-Dest"].orEmpty().lowercase()
                                                    val accept = headers["Accept"].orEmpty().lowercase()
                                                    when {
                                                        destination == "video" || destination == "audio" ->
                                                            sniff(url, hint = "video/")
                                                        accept.startsWith("video/") || accept.startsWith("audio/") ||
                                                            accept.contains("video/") || accept.contains("audio/") ->
                                                            sniff(url, hint = "video/")
                                                        lower.contains("videoplayback") || lower.contains("googlevideo.com") ->
                                                            sniff(url, hint = "video/mp4")
                                                        lower.contains(".m3u8") ->
                                                            sniff(url, hint = "application/x-mpegurl")
                                                        lower.contains(".mpd") ->
                                                            sniff(url, hint = "application/dash+xml")
                                                        looksLikeMedia(url) ->
                                                            sniff(url)
                                                    }
                                                }
                                            }
                                            return null
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onReceivedTitle(view: WebView?, title: String?) {
                                            super.onReceivedTitle(view, title)
                                            val clean = title?.replace(Regex("\\s*-\\s*YouTube\\s*$", RegexOption.IGNORE_CASE), "")?.trim()
                                            if (!clean.isNullOrBlank()) {
                                                currentPageTitle = clean
                                                videos = videos.map { if (it.title.isNullOrBlank()) it.copy(title = clean) else it }
                                            }
                                        }

                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress / 100f
                                            if (adSkip && newProgress in 15..80) {
                                                runCatching { view?.evaluateJavascript(AD_SKIP_JS, null) }
                                            }
                                        }
                                    }
                                    webRef.value = this
                                    loadUrl(currentUrl ?: "https://m.youtube.com")
                                }
                            },
                            update = { view ->
                                if (webRef.value !== view) webRef.value = view
                                view.setBackgroundColor(colors.surfaceVariant.toArgb())
                            },
                            onRelease = { view ->
                                if (webRef.value === view) webRef.value = null
                                runCatching { view.destroy() }
                            },
                        )
                        if (progress in 0.01f..0.99f) {
                            LinearProgressIndicator(
                                progress = { progress },
                                color = colors.primary,
                                trackColor = colors.surfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .height(3.dp),
                            )
                        }
                    }

                    DraggableSnifferFab(
                        count = visibleVideos.size,
                        scanning = scanning,
                        onClick = {
                            showVideos = true
                            scanPage()
                        },
                        containerWidth = containerWidth,
                        containerHeight = containerHeight,
                        marginPx = marginPx,
                    )
                }
            }
        }

        if (fullscreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primaryDeep)
                    .clickable { setFullscreen(false) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.FullscreenExit,
                        contentDescription = null,
                        tint = colors.onPrimaryDeep,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Exit full screen",
                        color = colors.onPrimaryDeep,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (showVideos && currentUrl != null) {
            SnifferSheet(
                videos = visibleVideos,
                filterIndex = sizeFilter,
                onFilter = { sizeFilter = it },
                adSkip = adSkip,
                onAdSkip = { adSkip = it },
                onPick = { url ->
                    showVideos = false
                    val targetUrl = when {
                        isWatchPageUrl(url) -> url
                        url.contains("googlevideo.com") || url.contains("videoplayback") -> {
                            val id = safeQueryParam(url, "id") ?: safeQueryParam(url, "docid")
                            if (!id.isNullOrBlank()) "https://www.youtube.com/watch?v=$id"
                            else currentUrl?.takeIf { isWatchPageUrl(it) } ?: url
                        }
                        else -> url
                    }
                    nav.navigate("analyzer/${android.net.Uri.encode(targetUrl)}")
                },
                onClear = { clearVideos() },
                onScan = { scanPage() },
                onDismiss = { showVideos = false },
            )
        }
    }
}

@Composable
private fun DraggableSnifferFab(
    count: Int,
    scanning: Boolean,
    onClick: () -> Unit,
    containerWidth: Float,
    containerHeight: Float,
    marginPx: Float = with(LocalDensity.current) { 12.dp.toPx() },
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var fabSize by remember { mutableStateOf(IntSize.Zero) }
    var offsetState by remember { mutableStateOf<Offset?>(null) }

    // Clamp coordinates within container bounds
    val minX = marginPx
    val maxX = (containerWidth - fabSize.width - marginPx).coerceAtLeast(minX)
    val minY = marginPx
    val maxY = (containerHeight - fabSize.height - marginPx).coerceAtLeast(minY)

    Row(
        modifier = modifier
            .onSizeChanged { fabSize = it }
            .offset {
                val pos = offsetState ?: Offset(maxX, maxY)
                IntOffset(
                    pos.x.coerceIn(minX, maxX).roundToInt(),
                    pos.y.coerceIn(minY, maxY).roundToInt(),
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "SnifferFab"
            }
            .pointerInput(containerWidth, containerHeight, fabSize) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isDrag = false
                    var totalDelta = Offset.Zero
                    var curPos = (offsetState ?: Offset(maxX, maxY)).let {
                        Offset(it.x.coerceIn(minX, maxX), it.y.coerceIn(minY, maxY))
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break

                        if (change.changedToUp()) {
                            change.consume()
                            if (!isDrag) {
                                onClick()
                            }
                            break
                        }

                        val delta = change.positionChange()
                        totalDelta += delta

                        if (!isDrag && totalDelta.getDistance() > touchSlop) {
                            isDrag = true
                        }

                        if (isDrag) {
                            change.consume()
                            curPos = Offset(
                                x = (curPos.x + delta.x).coerceIn(minX, maxX),
                                y = (curPos.y + delta.y).coerceIn(minY, maxY),
                            )
                            offsetState = curPos
                        }
                    }
                }
            }
            .shadow(14.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(colors.primaryDeep)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colors.onPrimaryDeep.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.VideoFile,
                contentDescription = null,
                tint = colors.onPrimaryDeep,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(9.dp))
        Column {
            Text(
                when {
                    count > 0 -> "$count video${if (count == 1) "" else "s"} found"
                    scanning -> "Scanning page…"
                    else -> "Sniff videos"
                },
                color = colors.onPrimaryDeep,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(
                if (scanning) "Checking sizes…" else "Tap to pick a stream",
                color = colors.onPrimaryDeep.copy(alpha = 0.72f),
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        if (scanning) {
            Spacer(Modifier.width(9.dp))
            CircularProgressIndicator(
                color = colors.onPrimaryDeep,
                strokeWidth = 1.6.dp,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnifferSheet(
    videos: List<SniffedVideo>,
    filterIndex: Int,
    onFilter: (Int) -> Unit,
    adSkip: Boolean,
    onAdSkip: (Boolean) -> Unit,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.textMuted) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Videos on this page",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${videos.size}",
                    color = colors.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Streams detected by the sniffer, filtered by size. Tiny thumbnails and bumps under ${humanBytes(MIN_VIDEO_BYTES)} are hidden.",
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                sizeFilterLabels.forEachIndexed { index, label ->
                    val active = filterIndex == index
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (active) colors.primaryDeep else colors.surfaceElevated)
                            .clickable { onFilter(index) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            label,
                            color = if (active) colors.onPrimaryDeep else colors.textSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(11.dp))

            if (videos.isEmpty()) {
                CardSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(15.dp)) {
                        Text(
                            "No matching video yet",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Start the video in the page, then tap Scan page. Some players only reveal the stream after playback begins.",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 330.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(videos, key = { it.url }) { video ->
                        SnifferVideoRow(video = video, onClick = { onPick(video.url) })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KineticButton(
                    label = "Scan page",
                    onClick = onScan,
                    tonal = true,
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                )
                KineticButton(
                    label = "Clear list",
                    onClick = onClear,
                    tonal = true,
                    enabled = videos.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    height = 44.dp,
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-skip ads",
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Clicks Skip/Close and fast-forwards in-page video ads.",
                        color = colors.textSecondary,
                        fontSize = 9.sp,
                        lineHeight = 13.sp,
                    )
                }
                Switch(
                    checked = adSkip,
                    onCheckedChange = onAdSkip,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimaryDeep,
                        checkedTrackColor = colors.primaryDeep,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surfaceVariant,
                    ),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SnifferVideoRow(
    video: SniffedVideo,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    val sizeText = when {
        video.effectiveSize > 0L -> humanBytes(video.effectiveSize)
        video.probing -> "checking size…"
        isWatchPageUrl(video.url) -> "Full Video (Multi-res)"
        video.playlist || isStreamingUrl(video.url) -> "Adaptive Stream"
        else -> "Ready"
    }
    val isAudio = video.displayQuality.contains("Audio", ignoreCase = true) ||
        video.mime?.startsWith("audio/") == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isAudio) Icons.Default.AudioFile else Icons.Default.VideoFile,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.displayTitle,
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Quality pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.primary.copy(alpha = 0.18f))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                ) {
                    Text(
                        text = video.displayQuality,
                        color = colors.primary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }

                // File size pill
                if (video.effectiveSize > 0L) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.secondary.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            text = sizeText,
                            color = colors.secondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(
                        text = sizeText,
                        color = colors.textSecondary,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }

                Text(
                    text = "· " + video.hostLabel,
                    color = colors.textMuted,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (video.probing) {
            CircularProgressIndicator(
                color = colors.primary,
                strokeWidth = 1.6.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.primaryDeep)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    "GET",
                    color = colors.onPrimaryDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}
