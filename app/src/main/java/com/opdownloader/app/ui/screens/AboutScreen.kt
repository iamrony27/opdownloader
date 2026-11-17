package com.opdownloader.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.opdownloader.app.BuildConfig
import com.opdownloader.app.R
import com.opdownloader.app.engine.MediaTools
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.ScreenPaddingNoTabs
import com.opdownloader.app.ui.components.SettingRow
import com.opdownloader.app.ui.components.UpdateCheckRow
import com.opdownloader.app.ui.theme.LocalAppColors

private data class Feature(val icon: ImageVector, val title: String, val body: String)

private val features = listOf(
    Feature(
        Icons.Default.CloudDownload,
        "Downloads that survive",
        "Byte-range requests keep the partial file on disk, so pausing, losing signal or killing the app never restarts a download from zero.",
    ),
    Feature(
        Icons.Default.VideoFile,
        "Video from 1000+ sites",
        "Bundled Python and yt-dlp read the real formats behind YouTube, Instagram, TikTok, X, Vimeo and many more, then merge them to MP4.",
    ),
    Feature(
        Icons.Default.MusicNote,
        "Audio when you want it",
        "Pick MP3 or M4A and FFmpeg extracts the audio on the phone. No upload, no online converter, no waiting queue.",
    ),
    Feature(
        Icons.Default.Public,
        "Browser with a sniffer",
        "A full in-app browser keeps your logins and watches the network for media, so you can grab the exact stream a page is playing.",
    ),
    Feature(
        Icons.Default.Folder,
        "Files where they belong",
        "Finished downloads are published through MediaStore, so they appear in Gallery, Files and every other app without digging through folders.",
    ),
    Feature(
        Icons.Default.Bolt,
        "A queue, not a button",
        "Run several transfers, pause all, resume all, retry failures, limit to Wi-Fi, or drop links from the share sheet while you keep using the app.",
    ),
)

@Composable
fun AboutScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val version = remember { runCatching { MediaTools.engineVersion(context) }.getOrNull() }

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
                title = "About",
                subtitle = "oP Downloader ${BuildConfig.VERSION_NAME}",
                showBack = true,
                onBack = { nav.popBackStack() },
            )
        }

        item {
            CardSurface {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_brand_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp)),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "oP Downloader",
                        color = colors.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fast, private downloads. No account, no ads.",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge("v${BuildConfig.VERSION_NAME}")
                        Badge("arm64")
                        Badge(version?.let { "yt-dlp $it" } ?: "yt-dlp bundled")
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "A free Android download manager with a real engine. Transfers, video extraction and file publishing all happen on the phone, so nothing is uploaded and no server ever sees your links.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        item {
            Text(
                "THE SHORT VERSION",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "oP Downloader started as a simple question: why do so many download apps need an account, a subscription, or a list of permissions just to save a file?",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This one does the job and stays out of the way. Paste a link, share one from another app, or open the built-in browser and let the sniffer find the stream. Choose a quality, press download, and the file lands in your gallery when it finishes. Pause it, close the app, come back tomorrow, and it continues where it stopped.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "There is no login, no tracking and no paid tier. It is built and maintained by one developer, so bug reports and stars on GitHub genuinely shape what gets fixed next.",
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        item {
            Text(
                "WHAT IT DOES",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        features.forEach { feature ->
            item {
                CardSurface {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(feature.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(feature.title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(3.dp))
                            Text(feature.body, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }

        item {
            Text(
                "UNDER THE HOOD",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(14.dp)) {
                    EngineLine("App", "${BuildConfig.APPLICATION_ID} · ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    EngineLine("Architecture", "Kotlin + Jetpack Compose (Material 3)")
                    EngineLine("HTTP", "OkHttp 4.12 · range resume")
                    EngineLine("Extractor", "youtubedl-android 0.18.1 · yt-dlp" + (version?.let { " $it" } ?: ""))
                    EngineLine("Media", "FFmpeg 0.18.1 · on-device merge")
                    EngineLine("Storage", "MediaStore (Q+) · FileProvider fallback")
                    EngineLine("Service", "Foreground dataSync · notification 1001")
                }
            }
        }

        item {
            Text(
                "THE DEVELOPER",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            CardSurface {
                Column {
                    SettingRow(
                        title = "iamrony27",
                        subtitle = "Solo developer · Android and backend work",
                        onClick = { openLink(context, SUPPORT_GITHUB) },
                        trailing = { Text("›", color = colors.textSecondary, fontSize = 20.sp) },
                    )
                    Divider()
                    SettingRow(
                        title = "Source code and releases",
                        subtitle = "github.com/iamrony27/opdownloader",
                        onClick = { openLink(context, APP_GITHUB_REPO) },
                        trailing = { Text("›", color = colors.textSecondary, fontSize = 20.sp) },
                    )
                    Divider()
                    Divider()
                    UpdateCheckRow()
                    Divider()
                    SettingRow(
                        title = "Star the repository ⭐",
                        subtitle = "The simplest way to support the project",
                        onClick = { openLink(context, APP_GITHUB_REPO) },
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.primary.copy(alpha = 0.16f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        },
                    )
                    Divider()
                    SettingRow(
                        title = "Report a bug or request a feature",
                        subtitle = "Open an issue on GitHub",
                        onClick = { openLink(context, APP_GITHUB_ISSUES) },
                        trailing = {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(17.dp),
                            )
                        },
                    )
                    Divider()
                    SettingRow(
                        title = "Support the project ☕",
                        subtitle = "UPI, Bitcoin or a coffee note",
                        onClick = { nav.navigate("support") },
                        trailing = { Text("›", color = colors.textSecondary, fontSize = 20.sp) },
                    )
                }
            }
        }

        item {
            CardSurface(color = colors.warning.copy(alpha = 0.10f)) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Download responsibly", color = colors.warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Only download content you own or that the rights holder allows. Some sites restrict downloads in their terms of service, and this app does not bypass DRM or paywalls.",
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }

        item {
            CardSurface {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Open-source credits", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "yt-dlp and youtube-dl (Unlicense/Public Domain) · youtubedl-android by JunkFood02 (GPL-3.0) · FFmpeg (LGPL/GPL) · OkHttp (Apache 2.0) · Coil (Apache 2.0) · Jetpack Compose (Apache 2.0). Engine integration inspired by FetchJet.",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Made by iamrony27 · v${BuildConfig.VERSION_NAME}",
                            color = colors.textMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun Badge(text: String) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text, color = colors.textSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun Divider() {
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
private fun EngineLine(label: String, value: String) {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = colors.textSecondary, fontSize = 10.sp, modifier = Modifier.width(78.dp))
        Text(value, color = colors.textPrimary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
    }
}
