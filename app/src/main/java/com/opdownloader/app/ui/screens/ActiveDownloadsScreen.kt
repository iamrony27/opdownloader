package com.opdownloader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.Store
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.data.humanSpeed
import com.opdownloader.app.engine.DownloadManager
import com.opdownloader.app.engine.FilePublisher
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CategoryChip
import com.opdownloader.app.ui.components.DownloadCard
import com.opdownloader.app.ui.components.EmptyState
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.ScreenPadding
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast

private val filters = listOf("ALL", "DOWNLOADING", "QUEUED", "PAUSED", "FAILED", "COMPLETED")

@Composable
fun ActiveDownloadsScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val downloads by Store.downloads.collectAsState()
    val aggregate by DownloadManager.aggregate.collectAsState()
    var filter by rememberSaveable { mutableStateOf("ALL") }

    val visible = downloads.filter { filter == "ALL" || it.status.name == filter }
    val running = downloads.filter { it.status == DownloadStatus.DOWNLOADING }
    val pending = downloads.filter { it.status == DownloadStatus.QUEUED }
    val paused = downloads.filter { it.status == DownloadStatus.PAUSED }
    val failed = downloads.filter { it.status == DownloadStatus.FAILED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppHeader(
                title = "Transfers",
                subtitle = buildString {
                    if (running.isNotEmpty()) append("${running.size} running")
                    if (pending.isNotEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        append("${pending.size} queued")
                    }
                    if (running.isEmpty() && pending.isEmpty()) append("Queue is idle")
                },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (running.isNotEmpty() || pending.isNotEmpty()) {
                            KineticButton(
                                label = "Pause",
                                onClick = { DownloadManager.pauseAll() },
                                icon = Icons.Default.Pause,
                                tonal = true,
                                height = 40.dp,
                            )
                        } else if (paused.isNotEmpty() || failed.isNotEmpty()) {
                            KineticButton(
                                label = "Resume",
                                onClick = { DownloadManager.resumeAll() },
                                icon = Icons.Default.PlayArrow,
                                height = 40.dp,
                            )
                        }
                    }
                },
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                filters.forEach { key ->
                    val count = if (key == "ALL") downloads.size else downloads.count { it.status.name == key }
                    if (key == "ALL" || count > 0 || filter == key) {
                        CategoryChip(
                            label = key.lowercase().replaceFirstChar { it.uppercase() },
                            selected = filter == key,
                            onClick = { filter = key },
                            count = count,
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = colors.primary, modifier = Modifier.height(14.dp).width(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (running.isEmpty()) "Live queue" else "${humanSpeed(aggregate.speedBps)} total",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${humanBytes(downloads.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.fileSize })} saved",
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                )
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Download,
                    title = if (filter == "ALL") "No transfers" else "No ${filter.lowercase()} downloads",
                    subtitle = if (filter == "ALL")
                        "Start a download from the Home tab and it will show up here with real speed and progress."
                    else "Try a different filter.",
                    buttonLabel = if (filter == "ALL") "Start a download" else null,
                    onButtonClick = if (filter == "ALL") {
                        { nav.navigate("home") }
                    } else null,
                )
            }
        } else {
            items(visible, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    onDetails = { nav.navigate("details/${it.id}") },
                    onPause = { DownloadManager.pause(it) },
                    onResume = { DownloadManager.start(it) },
                    onRetry = { DownloadManager.retry(it) },
                    onOpen = {
                        val ok = FilePublisher.openFile(context, item.fileUri, item.mimeType, item.publicPath)
                        if (!ok) toast(context, "No app can open this file")
                    },
                    onShare = {
                        FilePublisher.shareFile(context, item.fileUri, item.mimeType, item.fileName, item.publicPath)
                    },
                    onDelete = { target, deleteFiles -> DownloadManager.remove(target.id, deleteFiles) },
                )
            }
        }

        if (paused.isNotEmpty() || failed.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (paused.isNotEmpty()) {
                        KineticButton(
                            label = "Resume all (${paused.size})",
                            onClick = { DownloadManager.resumeAll() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.PlayArrow,
                            tonal = true,
                        )
                    }
                    if (failed.isNotEmpty()) {
                        KineticButton(
                            label = "Retry failed",
                            onClick = {
                                failed.forEach { DownloadManager.retry(it.id) }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Refresh,
                            tonal = true,
                        )
                    }
                }
            }
        }
    }
}
