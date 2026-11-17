package com.opdownloader.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.opdownloader.app.ui.components.KineticButton
import com.opdownloader.app.ui.components.backgroundRequestIntent
import com.opdownloader.app.ui.components.isBackgroundAllowed
import com.opdownloader.app.ui.theme.LocalAppColors

private data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val steps = listOf(
    OnboardingStep(
        "Lightning Speed Downloads",
        "Resumable HTTP downloads with live speed, real ETA and a real queue that keeps going in the background.",
        Icons.Default.Bolt,
    ),
    OnboardingStep(
        "Video & Stream Analyzer",
        "Paste a link from YouTube, Instagram, TikTok, Vimeo or any direct MP4 and pick the exact quality you want.",
        Icons.Default.VideoLibrary,
    ),
    OnboardingStep(
        "Background Downloads",
        "Allow notifications and battery exemption so transfers keep running with the screen off. Files land in your public Downloads folder or gallery.",
        Icons.Default.Notifications,
    ),
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val stepData = steps[step]

    // After notifications, ask for the battery exemption so downloads also
    // survive with the screen off. Both prompts happen only on the final step.
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onFinished() }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val backgroundIntent = backgroundRequestIntent(context)
        if (backgroundIntent != null && !isBackgroundAllowed(context)) {
            runCatching { backgroundLauncher.launch(backgroundIntent) }.onFailure { onFinished() }
        } else {
            onFinished()
        }
    }

    fun goNext() {
        if (step < steps.lastIndex) {
            step++
            return
        }
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val backgroundIntent = backgroundRequestIntent(context)
            if (backgroundIntent != null && !isBackgroundAllowed(context)) {
                runCatching { backgroundLauncher.launch(backgroundIntent) }.onFailure { onFinished() }
            } else {
                onFinished()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.7f))
        Crossfade(targetState = step, label = "onboarding") { current ->
            val data = steps[current]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(data.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(54.dp))
                }
                Spacer(Modifier.height(36.dp))
                Text(
                    data.title,
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    data.subtitle,
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.Center) {
            steps.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (index == step) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (index == step) colors.primaryDeep else colors.surfaceVariant),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        KineticButton(
            label = if (step == steps.lastIndex) "Get Started" else "Continue",
            onClick = { goNext() },
            modifier = Modifier.fillMaxWidth(),
            height = 52.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Android Engine 2.0",
            color = colors.textMuted,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
    }
}
