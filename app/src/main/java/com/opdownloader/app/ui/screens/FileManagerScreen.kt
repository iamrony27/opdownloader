package com.opdownloader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opdownloader.app.data.DownloadCategory
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.Store
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.engine.DownloadManager
import com.opdownloader.app.engine.FilePublisher
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.CategoryChip
import com.opdownloader.app.ui.components.DownloadCard
import com.opdownloader.app.ui.components.EmptyState
import com.opdownloader.app.ui.components.ProgressGauge
import com.opdownloader.app.ui.components.ScreenPadding
import com.opdownloader.app.ui.components.storageStats
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast
import androidx.navigation.NavHostController

@Composable
fun FileManagerScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val downloads by Store.downloads.collectAsState()
    var category by rememberSaveable { mutableStateOf("ALL") }
    var newestFirst by rememberSaveable { mutableStateOf(true) }

    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val totalSize = completed.sumOf { it.fileSize }
    val stats = remember(completed.size) { storageStats() }
    val usedRatio = if (stats.totalBytes > 0) stats.usedBytes.toFloat() / stats.totalBytes else 0f

    val visible = completed
        .filter { category == "ALL" || it.category.name == category }
        .let { list -> if (newestFirst) list.sortedByDescending { it.completedAt } else list.sortedByDescending { it.fileSize } }

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
                title = "Files",
                subtitle = "${completed.size} files • ${humanBytes(totalSize)}",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.opdownloader.app.ui.components.RoundIconButton(
                            icon = Icons.Default.Sort,
                            contentDescription = "Sort",
                            onClick = { newestFirst = !newestFirst },
                        )
                    }
                },
            )
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Downloaded", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(humanBytes(totalSize), color = colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Free space", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(humanBytes(stats.freeBytes), color = colors.tertiary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ProgressGauge(progress = usedRatio, color = colors.tertiary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Movies/oP Downloader • Music/oP Downloader • Downloads/oP Downloader",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                CategoryChip(
                    label = "All",
                    selected = category == "ALL",
                    onClick = { category = "ALL" },
                    count = completed.size,
                )
                DownloadCategory.entries.forEach { cat ->
                    val count = completed.count { it.category == cat }
                    if (count > 0 || category == cat.name) {
                        CategoryChip(
                            label = cat.label,
                            selected = category == cat.name,
                            onClick = { category = cat.name },
                            count = count,
                        )
                    }
                }
            }
        }

        if (visible.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Folder,
                    title = "No files here yet",
                    subtitle = "Completed downloads are published to your public Movies, Music and Downloads folders so other apps can see them.",
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

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surfaceElevated)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = colors.primary, modifier = Modifier.height(16.dp).width(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Tap a card's title to expand open, share and delete actions.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}
