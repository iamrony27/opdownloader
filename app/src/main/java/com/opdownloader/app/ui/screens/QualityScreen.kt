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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.opdownloader.app.data.PendingSelection
import com.opdownloader.app.data.QualityOption
import com.opdownloader.app.data.humanBytes
import com.opdownloader.app.ui.components.AppHeader
import com.opdownloader.app.ui.components.CardSurface
import com.opdownloader.app.ui.components.EmptyState
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.ScreenPaddingNoTabs
import com.opdownloader.app.ui.enqueueMediaDownload
import com.opdownloader.app.ui.theme.LocalAppColors

@Composable
fun QualityScreen(nav: NavHostController) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val video = PendingSelection.video

    if (video == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .statusBarsPadding()
                .padding(ScreenPaddingNoTabs),
        ) {
            AppHeader(title = "Quality", showBack = true, onBack = { nav.popBackStack() })
            EmptyState(
                icon = Icons.Default.VideoFile,
                title = "Nothing selected",
                subtitle = "Analyze a video link first, then open its quality list.",
                buttonLabel = "Go home",
                onButtonClick = {
                    nav.navigate("home")
                },
            )
        }
        return
    }

    var selected by remember { mutableStateOf<QualityOption?>(video.formats.firstOrNull { !it.audioOnly }) }

    fun start() {
        val option = selected ?: return
        enqueueMediaDownload(
            context = context,
            pageUrl = video.url,
            title = video.title,
            option = option,
            thumbnail = video.thumbnail,
            extractor = video.extractor,
        )
        nav.navigate("active") { popUpTo("home") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            AppHeader(
                title = "Quality",
                subtitle = "${video.formats.size} formats available",
                showBack = true,
                onBack = { nav.popBackStack() },
            )
            Spacer(Modifier.height(14.dp))
            CardSurface {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 88.dp, height = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceVariant),
                    ) {
                        if (!video.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = video.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            video.title,
                            color = colors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            video.extractor,
                            color = colors.textSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "VIDEO",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            items(video.formats.filter { !it.audioOnly }) { option ->
                SelectableQualityRow(
                    option = option,
                    selected = selected?.id == option.id,
                    onClick = { selected = option },
                )
            }
            val audio = video.formats.filter { it.audioOnly }
            if (audio.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "AUDIO ONLY",
                        color = colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                items(audio) { option ->
                    SelectableQualityRow(
                        option = option,
                        selected = selected?.id == option.id,
                        onClick = { selected = option },
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            KineticButton(
                label = selected?.let {
                    "Download ${it.label}" + if (it.sizeBytes > 0) " · ~${humanBytes(it.sizeBytes)}" else ""
                } ?: "Pick a quality",
                onClick = { start() },
                modifier = Modifier.fillMaxWidth(),
                enabled = selected != null,
                height = 52.dp,
            )
        }
    }
}

@Composable
private fun SelectableQualityRow(
    option: QualityOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.primary.copy(alpha = 0.14f) else colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (option.audioOnly) colors.secondary.copy(alpha = 0.16f) else colors.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (option.audioOnly) Icons.Default.AudioFile else Icons.Default.VideoFile,
                    contentDescription = null,
                    tint = if (option.audioOnly) colors.secondary else colors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(option.label, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(option.sublabel, color = colors.textSecondary, fontSize = 10.sp)
            }
            Text(
                if (option.sizeBytes > 0) humanBytes(option.sizeBytes) else "size unknown",
                color = colors.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(end = 10.dp),
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primaryDeep else colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = colors.onPrimaryDeep, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}
