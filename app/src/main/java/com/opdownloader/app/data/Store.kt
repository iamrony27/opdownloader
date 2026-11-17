package com.opdownloader.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object Store {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        load()
    }

    private fun storeFile(): File = File(appContext.filesDir, "premium_store.json")

    @Synchronized
    private fun load() {
        val file = storeFile()
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val settingsJson = root.optJSONObject("settings")
            if (settingsJson != null) {
                _settings.value = AppSettings(
                    themeMode = settingsJson.optString("themeMode", "dark"),
                    maxConcurrent = settingsJson.optInt("maxConcurrent", 3).coerceIn(1, 5),
                    wifiOnly = settingsJson.optBoolean("wifiOnly", false),
                    notifyOnProgress = settingsJson.optBoolean("notifyOnProgress", true),
                    autoRetry = settingsJson.optBoolean("autoRetry", true),
                    onboarded = settingsJson.optBoolean("onboarded", false),
                    engineSetupDone = settingsJson.optBoolean("engineSetupDone", false),
                )
            }
            val list = ArrayList<DownloadItem>()
            val array = root.optJSONArray("downloads") ?: JSONArray()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val item = itemFromJson(o)
                // A download that was running when the app died is not running now.
                list.add(
                    if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED)
                        item.copy(status = DownloadStatus.PAUSED, speedBps = 0, etaSeconds = 0)
                    else item
                )
            }
            _downloads.value = list
        }
    }

    private fun itemFromJson(o: JSONObject): DownloadItem {
        val bytes = o.optLong("downloadedBytes", 0)
        return DownloadItem(
            id = o.optString("id", UUID.randomUUID().toString()),
            url = o.optString("url"),
            fileName = o.optString("fileName", "download"),
            mimeType = o.optString("mimeType", "application/octet-stream"),
            category = runCatching {
                DownloadCategory.valueOf(o.optString("category", "OTHER"))
            }.getOrDefault(DownloadCategory.OTHER),
            status = runCatching {
                DownloadStatus.valueOf(o.optString("status", "PAUSED"))
            }.getOrDefault(DownloadStatus.PAUSED),
            fileSize = o.optLong("fileSize", 0),
            downloadedBytes = bytes,
            error = o.optString("error", "").ifBlank { null },
            fileUri = o.optString("fileUri", "").ifBlank { null },
            publicPath = o.optString("publicPath", "").ifBlank { null },
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            completedAt = o.optLong("completedAt", 0),
            ytDlp = o.optBoolean("ytDlp", false),
            formatSelector = o.optString("formatSelector", "").ifBlank { null },
            qualityHeight = o.optInt("qualityHeight", 0),
            audioOnly = o.optBoolean("audioOnly", false),
            sourceTitle = o.optString("sourceTitle", "").ifBlank { null },
            thumbnail = o.optString("thumbnail", "").ifBlank { null },
            pageUrl = o.optString("pageUrl", "").ifBlank { null },
            extractor = o.optString("extractor", "").ifBlank { null },
        )
    }

    private fun itemToJson(item: DownloadItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("url", item.url)
        put("fileName", item.fileName)
        put("mimeType", item.mimeType)
        put("category", item.category.name)
        put("status", item.status.name)
        put("fileSize", item.fileSize)
        put("downloadedBytes", item.downloadedBytes)
        put("error", item.error ?: "")
        put("fileUri", item.fileUri ?: "")
        put("publicPath", item.publicPath ?: "")
        put("createdAt", item.createdAt)
        put("completedAt", item.completedAt)
        put("ytDlp", item.ytDlp)
        put("formatSelector", item.formatSelector ?: "")
        put("qualityHeight", item.qualityHeight)
        put("audioOnly", item.audioOnly)
        put("sourceTitle", item.sourceTitle ?: "")
        put("thumbnail", item.thumbnail ?: "")
        put("pageUrl", item.pageUrl ?: "")
        put("extractor", item.extractor ?: "")
    }

    fun saveSoon() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(700)
            saveNow()
        }
    }

    @Synchronized
    fun saveNow() {
        runCatching {
            val root = JSONObject()
            val settingsJson = JSONObject().apply {
                put("themeMode", _settings.value.themeMode)
                put("maxConcurrent", _settings.value.maxConcurrent)
                put("wifiOnly", _settings.value.wifiOnly)
                put("notifyOnProgress", _settings.value.notifyOnProgress)
                put("autoRetry", _settings.value.autoRetry)
                put("onboarded", _settings.value.onboarded)
                put("engineSetupDone", _settings.value.engineSetupDone)
            }
            root.put("settings", settingsJson)
            val array = JSONArray()
            _downloads.value.forEach { array.put(itemToJson(it)) }
            root.put("downloads", array)
            val target = storeFile()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(root.toString())
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    fun add(item: DownloadItem) {
        _downloads.value = listOf(item) + _downloads.value
        saveSoon()
    }

    fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _downloads.value = _downloads.value.map { if (it.id == id) transform(it) else it }
        saveSoon()
    }

    fun get(id: String): DownloadItem? = _downloads.value.firstOrNull { it.id == id }

    fun remove(id: String) {
        _downloads.value = _downloads.value.filterNot { it.id == id }
        saveSoon()
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.value = transform(_settings.value)
        saveSoon()
    }

    fun clearHistory(keepFiles: Boolean = true) {
        _downloads.value = _downloads.value.filter { if (keepFiles) it.status != DownloadStatus.COMPLETED else false }
        saveNow()
    }

    fun clearEverything() {
        _downloads.value = emptyList()
        saveNow()
    }

    fun completedItems(): List<DownloadItem> =
        _downloads.value.filter { it.status == DownloadStatus.COMPLETED }
}
