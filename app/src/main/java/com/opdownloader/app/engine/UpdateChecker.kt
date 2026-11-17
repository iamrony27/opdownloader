package com.opdownloader.app.engine

import com.opdownloader.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/** Outcome of a GitHub "latest release" check. */
sealed class UpdateResult {
    data class Available(val version: String, val url: String, val notes: String) : UpdateResult()
    data object UpToDate : UpdateResult()
    data object NoReleases : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

/**
 * Checks the GitHub releases page for a newer version. The repository is empty
 * at the moment, so a 404 is treated as "no releases yet" instead of an error.
 */
object UpdateChecker {
    const val RELEASES_URL = "https://github.com/iamrony27/opdownloader/releases"
    private const val LATEST_API = "https://api.github.com/repos/iamrony27/opdownloader/releases/latest"

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "oP-Downloader/${BuildConfig.VERSION_NAME}")
            .build()
        try {
            Net.probeClient.newCall(request).execute().use { response ->
                when (response.code) {
                    404 -> UpdateResult.NoReleases
                    403 -> UpdateResult.Error("GitHub rate limit reached. Try again later.")
                    200 -> {
                        val body = response.body?.string().orEmpty()
                        if (body.isBlank()) return@use UpdateResult.Error("Empty response from GitHub")
                        val json = runCatching { JSONObject(body) }.getOrElse {
                            return@use UpdateResult.Error("Could not read GitHub response")
                        }
                        val tag = json.optString("tag_name").ifBlank { json.optString("name") }
                        val latest = tag.removePrefix("v").trim()
                        val current = BuildConfig.VERSION_NAME.trim()
                        if (latest.isNotBlank() && isNewer(latest, current)) {
                            UpdateResult.Available(
                                version = latest,
                                url = json.optString("html_url").ifBlank { RELEASES_URL },
                                notes = json.optString("body").trim(),
                            )
                        } else {
                            UpdateResult.UpToDate
                        }
                    }
                    else -> UpdateResult.Error("GitHub returned HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "Could not reach GitHub")
        }
    }

    /** Compares dotted numeric versions (1.0.2 > 1.0.1), ignoring extra text. */
    private fun isNewer(latest: String, current: String): Boolean {
        val a = numbers(latest)
        val b = numbers(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        Regex("\\d+").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()
}
