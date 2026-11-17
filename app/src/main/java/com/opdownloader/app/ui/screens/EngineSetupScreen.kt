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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.opdownloader.app.data.Store
import com.opdownloader.app.engine.EngineState
import com.opdownloader.app.engine.MediaTools
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.theme.LocalAppColors
import kotlinx.coroutines.delay

private enum class SetupPhase { Preparing, Updating, Done, Failed }

private data class SetupStep(
    val title: String,
    val subtitle: String,
)

private val setupSteps = listOf(
    SetupStep("Prepare media engine", "Python + FFmpeg runtime, bundled on-device"),
    SetupStep("Update yt-dlp", "Fetching the latest extractor release"),
    SetupStep("Ready to download", "No Settings visit needed — this is automatic"),
)

/**
 * Blocking first-run setup. Prepares the bundled runtime and updates yt-dlp
 * automatically so the user never has to open Settings to enable the engine.
 */
@Composable
fun EngineSetupScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val engineState by MediaTools.state.collectAsState()

    var phase by remember { mutableStateOf(SetupPhase.Preparing) }
    var detail by remember { mutableStateOf("Unpacking the bundled runtime…") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var autoRetried by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        Log.i("EngineSetup", "setup attempt=$attempt")
        error = null
        phase = SetupPhase.Preparing
        detail = "Unpacking Python and FFmpeg…"
        try {
            MediaTools.prepareStrict(context)
            Log.i("EngineSetup", "runtime prepared")
            phase = SetupPhase.Updating
            detail = "Checking the latest yt-dlp release…"
            detail = MediaTools.updateEngineStrict(context)
            Log.i("EngineSetup", "engine update: $detail")
            phase = SetupPhase.Done
            delay(650)
            Store.updateSettings { it.copy(engineSetupDone = true) }
            Log.i("EngineSetup", "first-run setup complete")
            onFinished()
        } catch (e: Exception) {
            phase = SetupPhase.Failed
            error = MediaTools.shortError(e)
            Log.e("EngineSetup", "setup failed", e)
            // One silent retry handles a flaky network or a partial unpack.
            if (!autoRetried) {
                autoRetried = true
                delay(1600)
                attempt += 1
            }
        }
    }

    val runtimeReady = engineState is EngineState.Ready
    val stepsDone = when (phase) {
        SetupPhase.Preparing -> 0
        SetupPhase.Updating -> 1
        SetupPhase.Done -> 3
        SetupPhase.Failed -> if (runtimeReady) 1 else 0
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(com.opdownloader.app.R.drawable.ic_brand_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(26.dp)),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = "Setting up oP Downloader",
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "One-time setup. The media engine is prepared and yt-dlp is updated for you.",
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                setupSteps.forEachIndexed { index, step ->
                    SetupStepRow(
                        step = step,
                        done = index < stepsDone,
                        active = when (phase) {
                            SetupPhase.Preparing -> index == 0
                            SetupPhase.Updating -> index == 1
                            else -> false
                        },
                        failed = phase == SetupPhase.Failed && index == stepsDone && !runtimeReady,
                    )
                }
                if (phase == SetupPhase.Preparing || phase == SetupPhase.Updating) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape),
                        color = colors.primary,
                        trackColor = colors.surfaceVariant,
                    )
                    Text(
                        text = detail,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                if (phase == SetupPhase.Done) {
                    Text(
                        text = detail,
                        color = colors.success,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (phase == SetupPhase.Failed) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Setup could not finish. Check your internet connection and try again.",
                    color = colors.error,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                KineticButton(
                    label = "Retry setup",
                    icon = Icons.Default.SystemUpdate,
                    onClick = {
                        autoRetried = false
                        attempt += 1
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                )
                if (runtimeReady) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Continue with the bundled engine",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onFinished() }
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        // Not persisted: the update is retried automatically next launch.
                    )
                }
            } else if (phase != SetupPhase.Done) {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Keep the app open for a moment",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp,
                )
            }
        }
    }
}

@Composable
private fun SetupStepRow(
    step: SetupStep,
    done: Boolean,
    active: Boolean,
    failed: Boolean,
) {
    val colors = LocalAppColors.current
    val tint = when {
        done -> colors.success
        failed -> colors.error
        active -> colors.primary
        else -> colors.textMuted
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (done || active) tint.copy(alpha = 0.16f) else colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                active -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
                else -> Icon(
                    when (step.title) {
                        "Update yt-dlp" -> Icons.Default.SystemUpdate
                        else -> Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                step.title,
                color = if (done || active) colors.textPrimary else colors.textSecondary,
                fontSize = 14.sp,
                fontWeight = if (done || active) FontWeight.SemiBold else FontWeight.Medium,
            )
            Text(
                step.subtitle,
                color = colors.textMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        if (active) {
            Text("Working…", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
