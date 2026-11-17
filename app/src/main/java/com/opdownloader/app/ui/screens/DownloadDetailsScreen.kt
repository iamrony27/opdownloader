package com.opdownloader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.opdownloader.app.data.DownloadItem
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.Store
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.data.humanDuration
import com.opdownloader.app.data.humanSpeed
import com.opdownloader.app.engine.DownloadManager
import com.opdownloader.app.engine.FilePublisher
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.DeleteDownloadDialog
import com.opdownloader.app.ui.components.EmptyState
import com.opdownloader.app.ui.components.InfoRow
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.ProgressGauge
import com.opdownloader.app.ui.components.ScreenPaddingNoTabs
import com.opdownloader.app.ui.components.StatusBadge
import com.opdownloader.app.ui.components.categoryColor
import com.opdownloader.app.ui.components.categoryIcon
import com.opdownloader.app.ui.components.formatDate
import com.opdownloader.app.ui.components.rememberFileMissing
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast

@Composable
fun DownloadDetailsScreen(nav: NavHostController, id: String) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val downloads by Store.downloads.collectAsState()
    val item = remember(downloads, id) { downloads.firstOrNull { it.id == id } }
    var confirmDelete by remember { mutableStateOf(false) }

    if (item == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .padding(ScreenPaddingNoTabs),
        ) {
            AppHeader(title = "Details", showBack = true, onBack = { nav.popBackStack() })
            EmptyState(
                icon = Icons.Default.ErrorOutline,
                title = "Download not found",
                subtitle = "It was removed from the queue.",
                buttonLabel = "Back to transfers",
                onButtonClick = { nav.navigate("active") },
            )
        }
        return
    }

    val accent = categoryColor(item.category, colors)
    val running = item.status == DownloadStatus.DOWNLOADING
    val fileMissing = rememberFileMissing(item)
    val isIndeterminate = running && (item.fileSize <= 0L || item.percent < 0f) && item.progress <= 0.001f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = ScreenPaddingNoTabs,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppHeader(
                title = "Details",
                subtitle = item.category.label,
                showBack = true,
                onBack = { nav.popBackStack() },
            )
        }

        if (!item.thumbnail.isNullOrBlank()) {
            item {
                CardSurface {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    ) {
                        AsyncImage(
                            model = item.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                item.extractor ?: "media",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(categoryIcon(item.category), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.fileName,
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusBadge(item.status, fileMissing = fileMissing)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    if (item.fileSize > 0) humanBytes(item.fileSize) else "unknown size",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    ProgressGauge(
                        progress = item.progress,
                        color = when (item.status) {
                            DownloadStatus.PAUSED -> colors.warning
                            DownloadStatus.FAILED -> colors.error
                            DownloadStatus.COMPLETED -> colors.success
                            else -> colors.primary
                        },
                        height = 8.dp,
                        indeterminate = isIndeterminate,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (isIndeterminate) "Streaming (Active)"
                            else "${(item.progress * 100).toInt().coerceIn(0, 100)}%",
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (running) {
                                (if (item.speedBps > 0) humanSpeed(item.speedBps) else "Active stream") +
                                    if (item.etaSeconds > 0) " · ${humanDuration(item.etaSeconds)} left" else ""
                            } else {
                                "${humanBytes(item.downloadedBytes)}" + if (item.fileSize > 0) " of ${humanBytes(item.fileSize)}" else ""
                            },
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (item.status) {
                            DownloadStatus.DOWNLOADING -> {
                                KineticButton(
                                    label = "Pause",
                                    onClick = { DownloadManager.pause(item.id) },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Pause,
                                    height = 46.dp,
                                )
                            }
                            DownloadStatus.QUEUED -> {
                                KineticButton(
                                    label = "Pause",
                                    onClick = { DownloadManager.pause(item.id) },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Pause,
                                    height = 46.dp,
                                )
                            }
                            DownloadStatus.PAUSED -> {
                                KineticButton(
                                    label = "Resume",
                                    onClick = { DownloadManager.start(item.id) },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.PlayArrow,
                                    height = 46.dp,
                                )
                            }
                            DownloadStatus.FAILED -> {
                                KineticButton(
                                    label = "Retry",
                                    onClick = { DownloadManager.retry(item.id) },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Refresh,
                                    height = 46.dp,
                                )
                            }
                            DownloadStatus.COMPLETED -> {
                                if (!fileMissing) KineticButton(
                                    label = "Open",
                                    onClick = {
                                        val ok = FilePublisher.openFile(context, item.fileUri, item.mimeType, item.publicPath)
                                        if (!ok) toast(context, "No app can open this file")
                                    },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.OpenInNew,
                                    height = 46.dp,
                                )
                            }
                        }
                        if (item.status == DownloadStatus.COMPLETED && !fileMissing) {
                            KineticButton(
                                label = "Share",
                                onClick = {
                                    FilePublisher.shareFile(context, item.fileUri, item.mimeType, item.fileName, item.publicPath)
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Share,
                                tonal = true,
                                height = 46.dp,
                            )
                        }
                        KineticButton(
                            label = "Delete",
                            onClick = { confirmDelete = true },
                            icon = Icons.Default.Delete,
                            color = colors.error,
                            height = 46.dp,
                        )
                    }
                }
            }
        }

        if (fileMissing) {
            item {
                CardSurface(color = colors.error.copy(alpha = 0.12f)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("File deleted", color = colors.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "This file is no longer on your phone. The download history is kept so you can remove the entry or download it again.",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }

        if (!item.error.isNullOrBlank()) {
            item {
                CardSurface(color = colors.error.copy(alpha = 0.12f)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Last error", color = colors.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(item.error!!, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Transfer information", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Type", item.mimeType)
                    InfoRow("Category", item.category.label)
                    InfoRow("Engine", if (item.ytDlp) "yt-dlp" else "HTTP (range resume)")
                    if (!item.formatSelector.isNullOrBlank()) InfoRow("Format", item.formatSelector!!)
                    InfoRow("Added", formatDate(item.createdAt))
                    if (item.completedAt > 0) InfoRow("Finished", formatDate(item.completedAt))
                    if (item.fileSize > 0) InfoRow("Size", humanBytes(item.fileSize))
                    if (!item.publicPath.isNullOrBlank()) InfoRow("Saved to", item.publicPath!!)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "SOURCE",
                        color = colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.url,
                        color = colors.textPrimary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
        }
    }

    if (confirmDelete) {
        DeleteDownloadDialog(
            item = item,
            onDismiss = { confirmDelete = false },
            onDeleteFile = {
                confirmDelete = false
                DownloadManager.remove(item.id, deleteFiles = true)
                nav.popBackStack()
            },
            onRemoveOnly = {
                confirmDelete = false
                DownloadManager.remove(item.id, deleteFiles = false)
                nav.popBackStack()
            },
        )
    }
}
