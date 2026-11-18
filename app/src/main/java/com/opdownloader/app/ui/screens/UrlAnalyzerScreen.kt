package com.opdownloader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.opdownloader.app.data.AnalysisResult
import com.opdownloader.app.data.PendingSelection
import com.opdownloader.app.data.QualityOption
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.data.humanDuration
import com.opdownloader.app.engine.EngineState
import com.opdownloader.app.engine.MediaTools
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.ScreenPaddingNoTabs
import com.opdownloader.app.ui.enqueueDirectDownload
import com.opdownloader.app.ui.enqueueMediaDownload
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast

private sealed interface AnalyzerState {
    data object Loading : AnalyzerState
    data class Ready(val result: AnalysisResult) : AnalyzerState
    data class Error(val message: String) : AnalyzerState
}

@Composable
fun UrlAnalyzerScreen(nav: NavHostController, url: String) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val engineState by MediaTools.state.collectAsState()
    val cleanUrl = remember(url) {
        val decoded = runCatching { android.net.Uri.decode(url) }.getOrDefault(url).trim()
        if (MediaTools.isPornhubUrl(decoded)) MediaTools.normalizePornhubUrl(decoded) else decoded
    }
    var state by remember(cleanUrl) { mutableStateOf<AnalyzerState>(AnalyzerState.Loading) }
    var attempt by remember(cleanUrl) { mutableStateOf(0) }

    LaunchedEffect(cleanUrl, attempt) {
        if (cleanUrl.isBlank()) {
            state = AnalyzerState.Error("No link was passed to the analyzer.")
            return@LaunchedEffect
        }
        state = AnalyzerState.Loading
        val result = runCatching { MediaTools.analyze(context, cleanUrl) }
            .getOrElse { AnalysisResult.Failure(MediaTools.shortError(it)) }
        state = when (result) {
            is AnalysisResult.Failure -> AnalyzerState.Error(result.message)
            else -> AnalyzerState.Ready(result)
        }
    }

    fun startVideo(video: AnalysisResult.Video, option: QualityOption) {
        enqueueMediaDownload(
            context = context,
            pageUrl = cleanUrl,
            title = video.title,
            option = option,
            thumbnail = video.thumbnail,
            extractor = video.extractor,
        )
        nav.navigate("active") {
            popUpTo("home")
        }
    }

    fun startDirect(direct: AnalysisResult.Direct) {
        enqueueDirectDownload(
            context = context,
            url = direct.url,
            fileName = direct.fileName,
            mimeType = direct.mimeType,
            sizeBytes = direct.sizeBytes,
        )
        nav.navigate("active") {
            popUpTo("home")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = ScreenPaddingNoTabs,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AppHeader(
                title = "Analyzer",
                subtitle = runCatching { android.net.Uri.parse(cleanUrl).host }.getOrNull() ?: "Reading the link",
                showBack = true,
                onBack = { nav.popBackStack() },
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    url,
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Copy",
                    color = colors.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("link", url))
                            toast(context, "Link copied")
                        }
                        .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                )
            }
        }

        when (val current = state) {
            is AnalyzerState.Loading -> {
                item {
                    CardSurface {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 34.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(color = colors.primary, strokeWidth = 3.dp)
                            Spacer(Modifier.height(18.dp))
                            Text("Analyzing link", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                when (engineState) {
                                    is EngineState.Preparing -> "Unpacking the media engine — the very first run can take a minute…"
                                    is EngineState.Failed -> "Media engine failed to start: ${(engineState as EngineState.Failed).message}"
                                    else -> "Probing the server, then asking yt-dlp for available formats."
                                },
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(horizontal = 10.dp),
                            )
                            Spacer(Modifier.height(20.dp))
                            KineticButton(
                                label = "Cancel",
                                onClick = { nav.popBackStack() },
                                tonal = true,
                                height = 42.dp,
                            )
                        }
                    }
                }
            }

            is AnalyzerState.Error -> {
                item {
                    CardSurface {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(62.dp)
                                    .clip(CircleShape)
                                    .background(colors.error.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Could not analyze this link", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                current.message,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(18.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KineticButton(
                                    label = "Try again",
                                    onClick = { attempt++ },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Refresh,
                                    height = 44.dp,
                                )
                                KineticButton(
                                    label = "Sniff in Browser",
                                    onClick = {
                                        nav.navigate("browser?url=${android.net.Uri.encode(cleanUrl)}") {
                                            popUpTo("home")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Public,
                                    height = 44.dp,
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Note: If downloading is not working, try connecting to a VPN to bypass ISP or regional blocks, or use the Web Sniffer.",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tip: if this is a private or login-only page, open it in the Web Sniffer and copy the media link.",
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }

            is AnalyzerState.Ready -> {
                when (val result = current.result) {
                    is AnalysisResult.Direct -> {
                        item {
                            CardSurface {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(colors.success.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = colors.success, modifier = Modifier.size(21.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Direct file detected", color = colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                "No video extraction needed",
                                                color = colors.textSecondary,
                                                fontSize = 11.sp,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colors.success.copy(alpha = 0.16f))
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        ) {
                                            Text("FILE", color = colors.success, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        result.fileName,
                                        color = colors.textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        InfoPill(if (result.sizeBytes > 0) humanBytes(result.sizeBytes) else "size unknown")
                                        InfoPill(result.mimeType.substringAfter('/').uppercase().take(10))
                                        InfoPill(if (result.resumable) "resumable" else "single stream")
                                    }
                                    Spacer(Modifier.height(18.dp))
                                    KineticButton(
                                        label = "Start download",
                                        onClick = { startDirect(result) },
                                        modifier = Modifier.fillMaxWidth(),
                                        icon = Icons.Default.FileDownload,
                                        height = 50.dp,
                                    )
                                }
                            }
                        }
                    }

                    is AnalysisResult.Video -> {
                        item {
                            CardSurface {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(colors.primaryContainer, colors.surfaceVariant),
                                                ),
                                            ),
                                    ) {
                                        if (!result.thumbnail.isNullOrBlank()) {
                                            AsyncImage(
                                                model = result.thumbnail,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.42f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                        }
                                        if (result.durationSeconds > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(10.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Color.Black.copy(alpha = 0.62f))
                                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                            ) {
                                                Text(
                                                    humanDuration(result.durationSeconds.toLong()),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            result.title,
                                            color = colors.textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = 22.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            InfoPill(result.extractor)
                                            InfoPill("${result.formats.size} formats")
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Choose quality", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "View all",
                                    color = colors.primary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        PendingSelection.video = result
                                        PendingSelection.option = null
                                        nav.navigate("quality")
                                    },
                                )
                            }
                        }

                        val videoOptions = result.formats.filter { !it.audioOnly }
                        items(videoOptions.size.coerceAtMost(5)) { index ->
                            val option = videoOptions[index]
                            QualityRow(option = option) { startVideo(result, option) }
                        }

                        item {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "AUDIO ONLY",
                                color = colors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                            )
                        }

                        val audioOptions = result.formats.filter { it.audioOnly }
                        items(audioOptions.size) { index ->
                            val option = audioOptions[index]
                            QualityRow(option = option, icon = true) { startVideo(result, option) }
                        }
                    }

                    is AnalysisResult.Failure -> {
                        item {
                            CardSurface {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Text("Nothing to download", color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    Text(result.message, color = colors.textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Video downloads are merged to MP4 with FFmpeg after the streams finish. Keep the app alive for the final merge.",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun QualityRow(option: QualityOption, icon: Boolean = false, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    CardSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (option.audioOnly) colors.secondary.copy(alpha = 0.16f) else colors.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (option.audioOnly) Icons.Default.AudioFile else Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = if (option.audioOnly) colors.secondary else colors.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(option.label, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(option.sublabel, color = colors.textSecondary, fontSize = 10.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (option.sizeBytes > 0) "~${humanBytes(option.sizeBytes)}" else "auto",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(colors.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("Download", color = colors.primary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}
