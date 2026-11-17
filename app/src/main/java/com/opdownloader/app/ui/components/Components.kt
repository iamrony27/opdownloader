package com.opdownloader.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.opdownloader.app.data.DownloadCategory
import com.opdownloader.app.data.DownloadItem
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.data.humanDuration
import com.opdownloader.app.data.humanSpeed
import com.opdownloader.app.engine.FilePublisher
import com.opdownloader.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceElevated)
                    .clickable { onBack?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = colors.textPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun KineticButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tonal: Boolean = false,
    enabled: Boolean = true,
    color: Color? = null,
    height: Dp = 50.dp,
) {
    val colors = LocalAppColors.current
    val background = when {
        !enabled -> colors.surfaceVariant.copy(alpha = 0.6f)
        color != null -> color
        tonal -> colors.surfaceVariant
        else -> colors.primaryDeep
    }
    val contentColor = when {
        !enabled -> colors.textMuted
        color != null -> colors.onPrimary
        tonal -> colors.textPrimary
        else -> colors.onPrimaryDeep
    }
    Row(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit, count: Int? = null) {
    val colors = LocalAppColors.current
    val background = if (selected) colors.primaryDeep else colors.surfaceVariant
    val content = if (selected) colors.onPrimaryDeep else colors.textSecondary
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.onPrimaryDeep.copy(alpha = 0.18f) else colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "$count",
                    color = content,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Remembers whether a finished download's file is still on the phone. The
 * history entry can outlive the file when the user deletes it from Files or
 * Gallery; the check is keyed so it only runs when the entry changes.
 */
@Composable
fun rememberFileMissing(item: DownloadItem): Boolean {
    val context = LocalContext.current
    return remember(item.id, item.status, item.fileUri, item.publicPath) {
        item.status == DownloadStatus.COMPLETED &&
            !FilePublisher.fileExists(context, item.fileUri, item.publicPath)
    }
}

@Composable
fun StatusBadge(status: DownloadStatus, fileMissing: Boolean = false) {
    val colors = LocalAppColors.current
    val (bg, fg) = when {
        fileMissing -> colors.error.copy(alpha = 0.16f) to colors.error
        else -> when (status) {
            DownloadStatus.DOWNLOADING -> colors.primary.copy(alpha = 0.18f) to colors.primary
            DownloadStatus.PAUSED -> colors.warning.copy(alpha = 0.16f) to colors.warning
            DownloadStatus.COMPLETED -> colors.success.copy(alpha = 0.16f) to colors.success
            DownloadStatus.FAILED -> colors.error.copy(alpha = 0.16f) to colors.error
            DownloadStatus.QUEUED -> colors.secondary.copy(alpha = 0.16f) to colors.secondary
        }
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(fg),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            if (fileMissing) "FILE DELETED" else status.label.uppercase(),
            color = fg,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    color: Color? = null,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color ?: colors.surfaceElevated),
    ) {
        content()
    }
}

@Composable
fun AuroraBackground(active: Boolean, colorsList: List<Color>, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 5200 else 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shift",
    )
    Canvas(modifier = modifier) {
        drawRect(color = colorsList.first().copy(alpha = 0.9f))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colorsList.getOrElse(1) { colorsList[0] }.copy(alpha = 0.75f), Color.Transparent),
            ),
            radius = size.minDimension * 1.05f,
            center = Offset(size.width * (0.25f + shift * 0.5f), size.height * (0.15f + shift * 0.35f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colorsList.getOrElse(2) { colorsList[0] }.copy(alpha = 0.55f), Color.Transparent),
            ),
            radius = size.minDimension * 0.9f,
            center = Offset(size.width * (0.85f - shift * 0.35f), size.height * (0.9f - shift * 0.25f)),
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.22f)),
            ),
        )
    }
}

@Composable
fun ShimmerMeshBackground(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    val transition = rememberInfiniteTransition(label = "meshShimmer")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "meshPulse",
    )
    val sweep by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "meshSweep",
    )

    Canvas(modifier = modifier.graphicsLayer()) {
        val w = size.width
        val h = size.height

        // Subtle themed ambient background fill
        val bgAlpha = if (colors.isDark) 0.12f else 0.08f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    accent.copy(alpha = bgAlpha * 1.5f),
                    colors.surfaceElevated,
                )
            )
        )

        // Radial glow anchored at top-left
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = if (colors.isDark) 0.22f + pulse * 0.12f else 0.16f + pulse * 0.08f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.25f, h * 0.3f),
                radius = w.coerceAtLeast(h) * 0.95f,
            ),
            center = Offset(w * 0.25f, h * 0.3f),
            radius = w.coerceAtLeast(h) * 0.95f,
        )

        // Radial glow anchored at bottom-right
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = if (colors.isDark) 0.15f + (1f - pulse) * 0.10f else 0.10f + (1f - pulse) * 0.06f),
                    Color.Transparent,
                ),
                center = Offset(w * 0.85f, h * 0.85f),
                radius = w.coerceAtLeast(h) * 0.8f,
            ),
            center = Offset(w * 0.85f, h * 0.85f),
            radius = w.coerceAtLeast(h) * 0.8f,
        )

        // Smooth subtle diagonal sweep
        val startX = w * sweep
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    (if (colors.isDark) Color.White else accent).copy(alpha = if (colors.isDark) 0.09f else 0.07f),
                    Color.Transparent,
                ),
                start = Offset(startX - 60f, 0f),
                end = Offset(startX + 60f, h),
            )
        )
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    animatedBackground: Boolean = false,
    aurora: Boolean = false,
    auroraActive: Boolean = false,
    auroraColors: List<Color> = emptyList(),
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated),
    ) {
        if (animatedBackground) {
            ShimmerMeshBackground(accent = accent, modifier = Modifier.matchParentSize())
        } else if (aurora && auroraColors.isNotEmpty()) {
            AuroraBackground(active = auroraActive, colorsList = auroraColors, modifier = Modifier.matchParentSize())
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    value,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp,
                    maxLines = 1,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    subtitle,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        }
    }
}

@Composable
fun ProgressGauge(
    progress: Float,
    color: Color,
    height: Dp = 6.dp,
    indeterminate: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    if (indeterminate) {
        androidx.compose.material3.LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape),
            color = color,
            trackColor = colors.surfaceVariant,
        )
    } else {
        Canvas(
            modifier = modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape),
        ) {
            drawRoundedBar(0f, 1f, colors.surfaceVariant)
            drawRoundedBar(0f, progress.coerceIn(0f, 1f), color)
        }
    }
}

private fun DrawScope.drawRoundedBar(start: Float, end: Float, color: Color) {
    if (end <= start) return
    drawRect(
        color = color,
        topLeft = Offset(size.width * start, 0f),
        size = Size(size.width * (end - start), size.height),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String? = null,
    onButtonClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        if (buttonLabel != null && onButtonClick != null) {
            Spacer(Modifier.height(20.dp))
            KineticButton(label = buttonLabel, onClick = onButtonClick)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color? = null, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = valueColor ?: colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.4f),
        )
    }
}

fun categoryIcon(category: DownloadCategory): ImageVector = when (category) {
    DownloadCategory.VIDEO -> Icons.Default.VideoFile
    DownloadCategory.AUDIO -> Icons.Default.AudioFile
    DownloadCategory.APK -> Icons.Default.Android
    DownloadCategory.ARCHIVE -> Icons.Default.Archive
    DownloadCategory.DOCUMENT -> Icons.Default.Description
    DownloadCategory.OTHER -> Icons.Default.Folder
}

fun categoryColor(category: DownloadCategory, colors: com.opdownloader.app.ui.theme.AppColors): Color = when (category) {
    DownloadCategory.VIDEO -> colors.primary
    DownloadCategory.AUDIO -> colors.secondary
    DownloadCategory.APK -> colors.tertiary
    DownloadCategory.ARCHIVE -> colors.info
    DownloadCategory.DOCUMENT -> colors.warning
    DownloadCategory.OTHER -> colors.textSecondary
}

@Composable
fun DownloadCard(
    item: DownloadItem,
    onDetails: (DownloadItem) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpen: (DownloadItem) -> Unit,
    onShare: (DownloadItem) -> Unit,
    onDelete: (DownloadItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var expanded by remember(item.id) { mutableStateOf(false) }
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    val bringIntoView = remember(item.id) { BringIntoViewRequester() }
    val icon = categoryIcon(item.category)
    val accent = categoryColor(item.category, colors)
    val isRunning = item.status == DownloadStatus.DOWNLOADING
    val isPaused = item.status == DownloadStatus.PAUSED
    val isDone = item.status == DownloadStatus.COMPLETED
    val isFailed = item.status == DownloadStatus.FAILED
    val fileMissing = rememberFileMissing(item)
    val isIndeterminate = isRunning && (item.fileSize <= 0L || item.percent < 0f) && item.progress <= 0.001f

    LaunchedEffect(expanded) {
        if (expanded) {
            // Wait for the expand animation, then pull the action row above the
            // bottom navigation so Share / Details / Delete are never clipped.
            delay(260)
            runCatching { bringIntoView.bringIntoView() }
        }
    }

    CardSurface(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { expanded = !expanded },
                ) {
                    Text(
                        item.fileName,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(item.status, fileMissing = fileMissing)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                fileMissing -> "File no longer on device"
                                item.fileSize > 0 -> "${humanBytes(item.downloadedBytes)} / ${humanBytes(item.fileSize)}"
                                item.downloadedBytes > 0 -> "${humanBytes(item.downloadedBytes)} downloaded (Streaming)"
                                isRunning -> "Connecting stream…"
                                else -> humanBytes(item.downloadedBytes)
                            },
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                QuickAction(
                    item = item,
                    onPause = onPause,
                    onResume = onResume,
                    onRetry = onRetry,
                    onOpen = onOpen,
                    fileMissing = fileMissing,
                )
            }

            if (isRunning || isPaused || item.status == DownloadStatus.QUEUED) {
                Spacer(Modifier.height(14.dp))
                ProgressGauge(
                    progress = item.progress,
                    color = if (isPaused) colors.warning else colors.primary,
                    indeterminate = isIndeterminate,
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isRunning) {
                            if (item.speedBps > 0) humanSpeed(item.speedBps) else "Downloading…"
                        } else if (isPaused) "Paused" else "Queued",
                        color = if (isPaused) colors.warning else colors.secondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            isRunning && item.etaSeconds > 0 -> "ETA ${humanDuration(item.etaSeconds)}"
                            isIndeterminate -> "Streaming • Active"
                            else -> "${(item.progress * 100).toInt()}%"
                        },
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (isIndeterminate) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Server did not provide total size upfront. Actively downloading stream.",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }

            if (isFailed && !item.error.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(item.error!!, color = colors.error, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }

            AnimatedVisibility(
                visible = expanded,
                modifier = Modifier.bringIntoViewRequester(bringIntoView),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (fileMissing) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = colors.error,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "File deleted from your phone. Remove this entry or download it again.",
                                    color = colors.error,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                )
                            }
                        }
                        if (isDone && !fileMissing) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                KineticButton(
                                    label = "Open",
                                    onClick = { onOpen(item) },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.OpenInNew,
                                    height = 42.dp,
                                )
                                KineticButton(
                                    label = "Share",
                                    onClick = { onShare(item) },
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Default.Share,
                                    tonal = true,
                                    height = 42.dp,
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KineticButton(
                                label = "Details",
                                onClick = { onDetails(item) },
                                modifier = Modifier.weight(1f),
                                tonal = true,
                                height = 42.dp,
                            )
                            KineticButton(
                                label = "Delete",
                                onClick = { confirmDelete = true },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Delete,
                                color = colors.error,
                                height = 42.dp,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("SOURCE URL", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(item.url, color = colors.textPrimary, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!item.publicPath.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("SAVED TO", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                        Text(item.publicPath!!, color = colors.textPrimary, fontSize = 11.sp, maxLines = 2)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteDownloadDialog(
            item = item,
            onDismiss = { confirmDelete = false },
            onDeleteFile = {
                confirmDelete = false
                onDelete(item, true)
            },
            onRemoveOnly = {
                confirmDelete = false
                onDelete(item, false)
            },
        )
    }
}

/**
 * Confirmation for the Delete action. Finished downloads let the user choose
 * between deleting the published file or only forgetting the list entry.
 */
@Composable
fun DeleteDownloadDialog(
    item: DownloadItem,
    onDismiss: () -> Unit,
    onDeleteFile: () -> Unit,
    onRemoveOnly: () -> Unit,
) {
    val colors = LocalAppColors.current
    val completed = item.status == DownloadStatus.COMPLETED
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceElevated)
                .padding(22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.error.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    if (completed) "Delete this download?" else "Remove this transfer?",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                item.fileName,
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (completed) {
                    val size = if (item.fileSize > 0) " · ${humanBytes(item.fileSize)}" else ""
                    "Choose whether to delete the saved file from storage or only remove this entry from the list.$size"
                } else {
                    "The unfinished transfer and its temporary file will be removed. Nothing has been published to storage yet."
                },
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(18.dp))
            if (completed) {
                KineticButton(
                    label = "Delete file and list entry",
                    onClick = onDeleteFile,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Delete,
                    color = colors.error,
                    height = 46.dp,
                )
                Spacer(Modifier.height(8.dp))
                KineticButton(
                    label = "Remove from list only",
                    onClick = onRemoveOnly,
                    modifier = Modifier.fillMaxWidth(),
                    tonal = true,
                    height = 46.dp,
                )
            } else {
                KineticButton(
                    label = "Remove transfer",
                    onClick = onRemoveOnly,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.Delete,
                    color = colors.error,
                    height = 46.dp,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun QuickAction(
    item: DownloadItem,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onRetry: (String) -> Unit,
    onOpen: (DownloadItem) -> Unit,
    fileMissing: Boolean = false,
) {
    val colors = LocalAppColors.current
    val (icon, tint, bg, action) = when {
        fileMissing -> Quad(Icons.Default.ErrorOutline, colors.error, colors.error.copy(alpha = 0.16f)) { }
        else -> when (item.status) {
            DownloadStatus.DOWNLOADING -> Quad(Icons.Default.Pause, colors.primary, colors.surfaceVariant) { onPause(item.id) }
            DownloadStatus.PAUSED, DownloadStatus.QUEUED -> Quad(Icons.Default.PlayArrow, colors.onPrimaryDeep, colors.primaryDeep) { onResume(item.id) }
            DownloadStatus.FAILED -> Quad(Icons.Default.Refresh, colors.onPrimary, colors.error) { onRetry(item.id) }
            DownloadStatus.COMPLETED -> Quad(Icons.Default.OpenInNew, colors.onPrimary, colors.success) { onOpen(item) }
        }
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable { action() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = colors.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onAction?.invoke() },
            )
        }
    }
}

@Composable
fun FileTypeIcon(mime: String, modifier: Modifier = Modifier) {
    val icon = when {
        mime.startsWith("image/") -> Icons.Default.Image
        mime.startsWith("video/") -> Icons.Default.VideoFile
        mime.startsWith("audio/") -> Icons.Default.AudioFile
        mime.contains("pdf") || mime.startsWith("text/") -> Icons.Default.Description
        mime.contains("zip") || mime.contains("rar") || mime.contains("7z") -> Icons.Default.Archive
        else -> Icons.Default.Folder
    }
    Icon(icon, contentDescription = null, modifier = modifier)
}
