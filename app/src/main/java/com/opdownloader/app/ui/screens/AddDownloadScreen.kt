package com.opdownloader.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.opdownloader.app.data.DownloadCategory
import com.opdownloader.app.data.fileNameFromUrl
import com.opdownloader.app.data.mediaMimeFor
import com.opdownloader.app.engine.MediaTools
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.ScreenPaddingNoTabs
import com.opdownloader.app.ui.components.readClipboard
import com.opdownloader.app.ui.enqueueDirectDownload
import com.opdownloader.app.ui.theme.LocalAppColors
import com.opdownloader.app.ui.toast

@Composable
fun AddDownloadScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    var url by rememberSaveable { mutableStateOf("") }
    var fileName by rememberSaveable { mutableStateOf("") }
    var nameTouched by rememberSaveable { mutableStateOf(false) }
    var categoryOverride by rememberSaveable { mutableStateOf<String?>(null) }

    val inferredName = remember(url) {
        if (url.isBlank()) "" else fileNameFromUrl(url).ifBlank { "download" }
    }
    val effectiveName = if (nameTouched) fileName else inferredName
    val inferredCategory = if (effectiveName.isBlank()) DownloadCategory.OTHER else DownloadCategory.fromFileName(effectiveName)

    fun start() {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            toast(context, "Enter a full http(s) link")
            return
        }
        if (effectiveName.isBlank()) {
            toast(context, "Give the file a name")
            return
        }
        val category = categoryOverride?.let { runCatching { DownloadCategory.valueOf(it) }.getOrNull() }
        enqueueDirectDownload(
            context = context,
            url = clean,
            fileName = effectiveName,
            mimeType = mediaMimeFor(effectiveName),
            sizeBytes = 0L,
            categoryHint = category,
        )
        nav.navigate("active") {
            popUpTo("add") { inclusive = true }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPaddingNoTabs),
    ) {
        AppHeader(
            title = "New download",
            subtitle = "Direct link · resume supported",
            showBack = true,
            onBack = { nav.popBackStack() },
        )
        Spacer(Modifier.height(16.dp))

        CardSurface {
            Column(modifier = Modifier.padding(16.dp)) {
                FieldLabel("FILE URL")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = colors.primary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (url.isEmpty()) {
                            Text("https://example.com/file.zip", color = colors.textMuted, fontSize = 13.sp, maxLines = 1)
                        }
                        BasicTextField(
                            value = url,
                            onValueChange = {
                                url = it
                                if (!nameTouched) fileName = ""
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.primary.copy(alpha = 0.16f))
                            .clickable {
                                val clip = readClipboard(context)
                                if (clip.isBlank()) {
                                    toast(context, "Clipboard is empty")
                                } else {
                                    url = clip
                                    if (!nameTouched) fileName = ""
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = colors.primary, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("SAVE AS")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (effectiveName.isEmpty()) {
                            Text("Detected from the link", color = colors.textMuted, fontSize = 13.sp)
                        }
                        BasicTextField(
                            value = effectiveName,
                            onValueChange = {
                                nameTouched = true
                                fileName = it
                            },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 13.sp),
                            cursorBrush = SolidColor(colors.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (nameTouched) {
                        Text(
                            "Reset",
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    nameTouched = false
                                    fileName = ""
                                }
                                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("CATEGORY")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    DownloadCategory.entries.forEach { cat ->
                        val selected = (categoryOverride ?: inferredCategory.name) == cat.name
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (selected) colors.primaryDeep else colors.surfaceVariant)
                                .clickable { categoryOverride = cat.name }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cat.label.removeSuffix("s").take(4),
                                color = if (selected) colors.onPrimaryDeep else colors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Auto-detected as ${inferredCategory.label}. Files are published to the matching public folder.",
                    color = colors.textMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        CardSurface(color = colors.primary.copy(alpha = 0.12f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val clean = url.trim()
                        if (clean.isBlank()) {
                            toast(context, "Paste a link first")
                        } else {
                            val targetUrl = if (MediaTools.isPornhubUrl(clean)) {
                                MediaTools.normalizePornhubUrl(clean)
                            } else {
                                Regex("""https?://[^\s<>"']+""").find(clean)?.value ?: clean
                            }
                            nav.navigate("analyzer/${android.net.Uri.encode(targetUrl)}")
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Is it a video page?", color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Run the smart analyzer instead and pick an exact quality.",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                    )
                }
                Text("Analyze", color = colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(18.dp))
        KineticButton(
            label = "Start download",
            onClick = { start() },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.FileDownload,
            height = 52.dp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "The transfer resumes from the last byte if you pause it or the app restarts.",
            color = colors.textMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        color = LocalAppColors.current.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.1.sp,
    )
}
