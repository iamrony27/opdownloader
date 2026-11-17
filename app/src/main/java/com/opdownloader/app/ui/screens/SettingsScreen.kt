package com.opdownloader.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.opdownloader.app.BuildConfig
import com.opdownloader.app.data.DownloadStatus
import com.opdownloader.app.data.Store
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.engine.DownloadManager
import com.opdownloader.app.engine.EngineState
import com.opdownloader.app.engine.MediaTools
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.BackgroundPermissionRow
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.ScreenPadding
import com.opdownloader.app.ui.components.SettingRow
import com.opdownloader.app.ui.components.UpdateCheckRow
import com.opdownloader.app.ui.components.storageStats
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Store.settings.collectAsState()
    val downloads by Store.downloads.collectAsState()
    val engineState by MediaTools.state.collectAsState()

    var version by remember { mutableStateOf(MediaTools.engineVersion(context)) }
    var engineBusy by remember { mutableStateOf(false) }
    var engineMessage by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    val completed = downloads.filter { it.status == DownloadStatus.COMPLETED }
    val finishedBytes = completed.sumOf { it.fileSize }
    val stats = remember(completed.size) { storageStats() }

    LaunchedEffect(engineState) {
        if (engineState is EngineState.Ready) version = MediaTools.engineVersion(context)
    }

    fun refreshEngineInfo() {
        version = MediaTools.engineVersion(context)
    }

    var showBtcQrDialog by remember { mutableStateOf(false) }
    var showUpiQrDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            AppHeader(title = "Settings", subtitle = "Tune the engine and storage")
        }

        item { SettingsSectionLabel("Appearance", Icons.Default.Palette) }
        item {
            CardSurface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Theme", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Light and dark palettes are fully hand-tuned.", color = colors.textSecondary, fontSize = 11.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (key, label) ->
                            val selected = settings.themeMode == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.primaryDeep else colors.surfaceVariant)
                                    .clickable { Store.updateSettings { it.copy(themeMode = key) } }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    color = if (selected) colors.onPrimaryDeep else colors.textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { SettingsSectionLabel("Downloads", Icons.Default.Download) }
        item {
            CardSurface {
                Column {
                    SettingRow(
                        title = "Simultaneous downloads",
                        subtitle = "How many files run at the same time (1–5).",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(colors.surfaceVariant)
                                        .clickable {
                                            Store.updateSettings { it.copy(maxConcurrent = (it.maxConcurrent - 1).coerceAtLeast(1)) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text("−", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                                Text(
                                    "${settings.maxConcurrent}",
                                    color = colors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(colors.surfaceVariant)
                                        .clickable {
                                            Store.updateSettings { it.copy(maxConcurrent = (it.maxConcurrent + 1).coerceAtMost(5)) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { Text("+", color = colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                            }
                        },
                    )
                    SettingsDivider()
                    SettingRow(
                        title = "Wi-Fi only",
                        subtitle = "Pause the queue when mobile data is the active network.",
                        trailing = {
                            Switch(
                                checked = settings.wifiOnly,
                                onCheckedChange = { checked ->
                                    Store.updateSettings { it.copy(wifiOnly = checked) }
                                },
                                colors = switchColors(),
                            )
                        },
                    )
                    SettingsDivider()
                    SettingRow(
                        title = "Live progress notification",
                        subtitle = "Show a foreground notification with progress while downloading.",
                        trailing = {
                            Switch(
                                checked = settings.notifyOnProgress,
                                onCheckedChange = { checked ->
                                    Store.updateSettings { it.copy(notifyOnProgress = checked) }
                                },
                                colors = switchColors(),
                            )
                        },
                    )
                    SettingsDivider()
                    BackgroundPermissionRow()
                    SettingsDivider()
                    SettingRow(
                        title = "Auto retry",
                        subtitle = "Retry a failed transfer up to two times before giving up.",
                        trailing = {
                            Switch(
                                checked = settings.autoRetry,
                                onCheckedChange = { checked ->
                                    Store.updateSettings { it.copy(autoRetry = checked) }
                                },
                                colors = switchColors(),
                            )
                        },
                    )
                }
            }
        }

        item { SettingsSectionLabel("Media engine", Icons.Default.SettingsSuggest) }
        item {
            CardSurface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("yt-dlp + FFmpeg", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                version?.let { "yt-dlp $it" } ?: if (engineState is EngineState.Ready) "Runtime ready · Python + FFmpeg" else "Bundled runtime · not prepared yet",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        when (engineState) {
                            is EngineState.Preparing -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.primary,
                            )
                            is EngineState.Ready -> Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.success.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("READY", color = colors.success, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            is EngineState.Failed -> Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.error.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("ERROR", color = colors.error, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            else -> Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("IDLE", color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (engineState as? EngineState.Failed)?.message
                            ?: "Extracts video and audio from YouTube, Instagram, TikTok, Vimeo and 1000+ other sites. The first run unpacks Python and FFmpeg, which can take a minute.",
                        color = if (engineState is EngineState.Failed) colors.error else colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                    if (engineMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(engineMessage!!, color = colors.secondary, fontSize = 11.sp, lineHeight = 15.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KineticButton(
                            label = if (engineBusy) "Working…" else "Prepare engine",
                            onClick = {
                                if (!engineBusy) {
                                    engineBusy = true
                                    engineMessage = null
                                    scope.launch {
                                        MediaTools.prepare(context)
                                        refreshEngineInfo()
                                        engineBusy = false
                                        engineMessage = when (val s = MediaTools.state.value) {
                                            is EngineState.Failed -> "Engine error: ${s.message}"
                                            else -> "Engine is ready to use"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Memory,
                            tonal = true,
                            enabled = !engineBusy,
                            height = 44.dp,
                        )
                        KineticButton(
                            label = if (engineBusy) "Working…" else "Update yt-dlp",
                            onClick = {
                                if (!engineBusy) {
                                    engineBusy = true
                                    engineMessage = null
                                    scope.launch {
                                        engineMessage = MediaTools.updateEngine(context)
                                        refreshEngineInfo()
                                        engineBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.SystemUpdate,
                            enabled = !engineBusy,
                            height = 44.dp,
                        )
                    }
                }
            }
        }

        item { SettingsSectionLabel("Storage", Icons.Default.Storage) }
        item {
            CardSurface {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Downloads on this device", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${completed.size} files · ${humanBytes(finishedBytes)}",
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        Text(
                            "${humanBytes(stats.freeBytes)} free",
                            color = colors.tertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KineticButton(
                            label = "Clear history",
                            onClick = {
                                Store.clearHistory(keepFiles = true)
                                toast(context, "History cleared, files kept")
                            },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.CleaningServices,
                            tonal = true,
                            height = 44.dp,
                        )
                        KineticButton(
                            label = "Delete all",
                            onClick = { confirmWipe = true },
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.DeleteForever,
                            color = colors.error,
                            height = 44.dp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Clear history forgets completed transfers but leaves the files in your gallery. Delete all removes those files too.",
                        color = colors.textMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }

        item { SettingsSectionLabel("Community", Icons.Default.Star) }
        item {
            CardSurface {
                Column {
                    SettingRow(
                        title = "Star oP Downloader on GitHub ⭐",
                        subtitle = "It is free and open source — a star helps other people find it",
                        onClick = { openLink(context, APP_GITHUB_REPO) },
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.primary.copy(alpha = 0.16f))
                                    .clickable { openLink(context, APP_GITHUB_REPO) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = colors.primary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Star", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                    )
                    SettingsDivider()
                    SettingRow(
                        title = "Bugs & feature requests",
                        subtitle = "Open an issue on the GitHub repository",
                        onClick = { openLink(context, APP_GITHUB_ISSUES) },
                        trailing = { Text("›", color = colors.textSecondary, fontSize = 20.sp) },
                    )
                }
            }
        }

        item { SettingsSectionLabel("Support", Icons.Default.Favorite) }
        item {
            CardSurface {
                Column {
                    SettingRow(
                        title = "Buy me a coffee ☕",
                        subtitle = "One dev, no ads — a note from the developer",
                        onClick = { nav.navigate("support") },
                        trailing = { Text("›", color = colors.textSecondary, fontSize = 20.sp) },
                    )
                    SettingsDivider()
                    SettingRow(
                        title = "UPI · $SUPPORT_UPI",
                        subtitle = "Tap to view QR · or copy the ID",
                        onClick = { showUpiQrDialog = true },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.primary.copy(alpha = 0.16f))
                                        .clickable { showUpiQrDialog = true }
                                        .padding(horizontal = 7.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = colors.primary, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("QR", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant)
                                        .clickable {
                                            copyToClipboard(context, SUPPORT_UPI)
                                            toast(context, "UPI ID copied")
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Copy", color = colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                    )
                    SettingsDivider()
                    SettingRow(
                        title = "Bitcoin (BTC)",
                        subtitle = "Tap to show QR · on-chain SegWit",
                        onClick = { showBtcQrDialog = true },
                        trailing = {
                            val btcYellow = Color(0xFFE5A100)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(btcYellow.copy(alpha = 0.16f))
                                        .clickable { showBtcQrDialog = true }
                                        .padding(horizontal = 7.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.QrCode, contentDescription = "Show QR", tint = btcYellow, modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(3.dp))
                                        Text("QR", color = btcYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceVariant)
                                        .clickable {
                                            copyToClipboard(context, SUPPORT_BTC)
                                            toast(context, "BTC address copied")
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Copy", color = btcYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                    )
                    SettingsDivider()
                    SettingRow(
                        title = "Work with me on your project",
                        subtitle = "github.com/iamrony27 · wa.me/917074018142",
                        onClick = { nav.navigate("support") },
                        trailing = { Text("›", color = colors.textSecondary, fontSize = 20.sp) },
                    )
                }
            }
        }

        item { SettingsSectionLabel("About", Icons.Default.Info) }
        item {
            CardSurface {
                Column {
                    SettingRow(
                        title = "About oP Downloader",
                        subtitle = "Engine credits, version and legal notes",
                        onClick = { nav.navigate("about") },
                        trailing = { Text("›", color = colors.textSecondary, fontSize = 20.sp) },
                    )
                    SettingsDivider()
                    UpdateCheckRow()
                    SettingsDivider()
                    SettingRow(
                        title = "Version",
                        subtitle = "Build ${BuildConfig.VERSION_CODE}",
                        trailing = { Text(BuildConfig.VERSION_NAME, color = colors.textSecondary, fontSize = 12.sp) },
                    )
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = { Text("Delete all downloads?") },
            text = {
                Text(
                    "This removes ${completed.size} published file(s) and every history record. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        DownloadManager.deleteCompletedFiles(completed)
                        Store.clearEverything()
                        confirmWipe = false
                        toast(context, "All downloads deleted")
                    },
                ) {
                    Text("Delete everything", color = colors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
        )
    }

    if (showBtcQrDialog) {
        val btcYellow = Color(0xFFE5A100)
        AlertDialog(
            onDismissRequest = { showBtcQrDialog = false },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(btcYellow.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.CurrencyBitcoin,
                            contentDescription = null,
                            tint = btcYellow,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Bitcoin Donation QR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Scan with any crypto wallet (Cash App, Binance, Strike, etc.)",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(2.dp, btcYellow.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                    ) {
                        QrCodePatternCanvas(
                            pattern = BTC_QR_PATTERN,
                            modifier = Modifier.size(190.dp),
                            color = Color.Black,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        SUPPORT_BTC,
                        color = colors.textPrimary,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 14.sp,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, SUPPORT_BTC)
                        toast(context, "BTC address copied")
                    },
                ) {
                    Text("Copy Address", color = btcYellow, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBtcQrDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            },
        )
    }

    if (showUpiQrDialog) {
        AlertDialog(
            onDismissRequest = { showUpiQrDialog = false },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("UPI Donation QR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Scan with GPay, PhonePe, Paytm, or any UPI app",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(2.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                    ) {
                        QrCodePatternCanvas(
                            pattern = UPI_QR_PATTERN,
                            modifier = Modifier.size(190.dp),
                            color = Color.Black,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        SUPPORT_UPI,
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, SUPPORT_UPI)
                        toast(context, "UPI ID copied")
                    },
                ) {
                    Text("Copy UPI", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpiQrDialog = false }) {
                    Text("Close", color = colors.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(icon, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            color = colors.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun SettingsDivider() {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(colors.divider),
    )
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = LocalAppColors.current.onPrimaryDeep,
    checkedTrackColor = LocalAppColors.current.primaryDeep,
    uncheckedThumbColor = LocalAppColors.current.textMuted,
    uncheckedTrackColor = LocalAppColors.current.surfaceVariant,
    uncheckedBorderColor = LocalAppColors.current.border,
)
