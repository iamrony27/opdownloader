package com.opdownloader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.opdownloader.app.R
import com.opdownloader.app.data.DownloadCategory
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.Store
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.data.humanSpeed
import com.opdownloader.app.engine.DownloadManager
import com.opdownloader.app.engine.FilePublisher
import com.opdownloader.app.engine.MediaTools
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.CategoryChip
import com.opdownloader.app.ui.components.DownloadCard
import com.opdownloader.app.ui.components.EmptyState
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.MetricCard
import com.opdownloader.app.ui.components.ProgressGauge
import com.opdownloader.app.ui.components.SectionTitle
import com.opdownloader.app.ui.components.ScreenPadding
import com.opdownloader.app.ui.components.ThemeToggleButton
import com.opdownloader.app.ui.components.readClipboard
import com.opdownloader.app.ui.components.storageStats
import com.opdownloader.app.ui.enqueueDirectDownload
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast

@Composable
fun HomeScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val downloads by Store.downloads.collectAsState()
    val aggregate by DownloadManager.aggregate.collectAsState()

    var url by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("ALL") }

    val active = downloads.filter { it.status == DownloadStatus.DOWNLOADING }
    val waiting = downloads.filter { it.status == DownloadStatus.QUEUED }
    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val stats = remember(downloads.size) { storageStats() }

    // Running transfers are shown once, under "In progress". The Downloads list is
    // reserved for finished, failed and paused items so a live progress bar never
    // appears twice on Home.
    val settled = downloads.filter {
        it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.QUEUED
    }
    val categories = listOf("ALL") + DownloadCategory.entries.map { it.name }
    val filtered = settled.filter { selectedCategory == "ALL" || it.category.name == selectedCategory }

    fun analyze() {
        val clean = url.trim()
        if (clean.isBlank()) {
            toast(context, "Paste a link first")
            return
        }
        val targetUrl = if (MediaTools.isPornhubUrl(clean)) {
            MediaTools.normalizePornhubUrl(clean)
        } else {
            Regex("""https?://[^\s<>"']+""").find(clean)?.value ?: clean
        }
        nav.navigate("analyzer/${android.net.Uri.encode(targetUrl)}")
        url = ""
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.splash_logo_transparent),
                    contentDescription = "oP Downloader logo",
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "oP Downloader",
                    color = colors.primaryDeep,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = (-0.8).sp,
                    modifier = Modifier.weight(1f),
                )
                ThemeToggleButton()
            }
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (url.isEmpty()) {
                                Text(
                                    "Paste URL (HTTP, video, APK, MP3)…",
                                    color = colors.textMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = url,
                                onValueChange = { url = it },
                                singleLine = true,
                                textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                                cursorBrush = SolidColor(colors.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (url.isBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(colors.primary.copy(alpha = 0.16f))
                                    .clickable {
                                        val clip = readClipboard(context)
                                        if (clip.isBlank()) toast(context, "Clipboard is empty") else url = clip
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Paste", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Text(
                                "Clear",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { url = "" }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KineticButton(
                            label = "Analyze & Download",
                            onClick = { analyze() },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Bolt,
                            height = 48.dp,
                        )
                        KineticButton(
                            label = "Manual",
                            onClick = { nav.navigate("add") },
                            icon = Icons.Default.Add,
                            tonal = true,
                            height = 48.dp,
                        )
                    }
                }
            }
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.tertiary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Storage,
                                contentDescription = null,
                                tint = colors.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Device Storage", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${humanBytes(stats.freeBytes)} free of ${humanBytes(stats.totalBytes)}",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${humanBytes(stats.usedBytes)} used",
                            color = colors.tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    val ratio = if (stats.totalBytes > 0) stats.usedBytes.toFloat() / stats.totalBytes else 0f
                    ProgressGauge(progress = ratio, color = colors.tertiary, height = 7.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Finished files are published to Movies, Music and Downloads › oP Downloader",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricCard(
                    title = "ACTIVE",
                    value = "${active.size + waiting.size}",
                    subtitle = if (active.isNotEmpty()) humanSpeed(aggregate.speedBps) else "nothing running",
                    icon = Icons.Default.Download,
                    accent = colors.primary,
                    animatedBackground = true,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "SPEED",
                    value = humanSpeed(if (active.isEmpty()) 0L else aggregate.speedBps),
                    subtitle = if (active.isEmpty()) "idle" else "${active.size} streams",
                    icon = Icons.Default.Bolt,
                    accent = colors.secondary,
                    animatedBackground = true,
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "COMPLETED",
                    value = "${completed.size}",
                    subtitle = if (completed.isEmpty()) "no files yet" else humanBytes(completed.sumOf { it.fileSize }),
                    icon = Icons.Default.CheckCircle,
                    accent = colors.success,
                    animatedBackground = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (active.isNotEmpty() || waiting.isNotEmpty()) {
            item {
                SectionTitle(
                    text = "In progress",
                    action = "View all",
                    onAction = { nav.navigate("active") },
                )
            }
            items((active + waiting).take(2), key = { "active-${it.id}" }) { item ->
                DownloadCard(
                    item = item,
                    onDetails = { nav.navigate("details/${it.id}") },
                    onPause = { DownloadManager.pause(it) },
                    onResume = { DownloadManager.start(it) },
                    onRetry = { DownloadManager.retry(it) },
                    onOpen = {
                        val ok = FilePublisher.openFile(context, it.fileUri, it.mimeType, it.publicPath)
                        if (!ok) toast(context, "No app can open this file")
                    },
                    onShare = {
                        FilePublisher.shareFile(context, it.fileUri, it.mimeType, it.fileName, it.publicPath)
                    },
                    onDelete = { target, deleteFiles -> DownloadManager.remove(target.id, deleteFiles) },
                )
            }
        }

        item {
            SectionTitle(
                text = "Downloads",
                action = if (downloads.isNotEmpty()) "New" else null,
                onAction = { nav.navigate("add") },
            )
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                CategoryChip(
                    label = "All",
                    selected = selectedCategory == "ALL",
                    onClick = { selectedCategory = "ALL" },
                    count = settled.size,
                )
                DownloadCategory.entries.forEach { category ->
                    val count = settled.count { it.category == category }
                    if (count > 0 || selectedCategory == category.name) {
                        CategoryChip(
                            label = category.label,
                            selected = selectedCategory == category.name,
                            onClick = { selectedCategory = category.name },
                            count = count,
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                when {
                    downloads.isEmpty() -> EmptyState(
                        icon = Icons.Default.Download,
                        title = "No downloads yet",
                        subtitle = "Paste any link above and the analyzer will work out whether it is a direct file or a video page.",
                    )
                    settled.isEmpty() -> EmptyState(
                        icon = Icons.Default.Download,
                        title = "Downloads in progress",
                        subtitle = "Finished, failed and paused files will appear here once they leave the In progress section.",
                    )
                    else -> EmptyState(
                        icon = Icons.Default.Download,
                        title = "Nothing in this category",
                        subtitle = "Pick another category to see your files.",
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                DownloadCard(
                    item = item,
                    onDetails = { nav.navigate("details/${it.id}") },
                    onPause = { DownloadManager.pause(it) },
                    onResume = { DownloadManager.start(it) },
                    onRetry = { DownloadManager.retry(it) },
                    onOpen = {
                        val ok = FilePublisher.openFile(context, it.fileUri, it.mimeType, it.publicPath)
                        if (!ok) toast(context, "No app can open this file")
                    },
                    onShare = {
                        FilePublisher.shareFile(context, it.fileUri, it.mimeType, it.fileName, it.publicPath)
                    },
                    onDelete = { target, deleteFiles -> DownloadManager.remove(target.id, deleteFiles) },
                )
            }
        }

    }
}
