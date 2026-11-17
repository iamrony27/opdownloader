package com.opdownloader.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opdownloader.app.BuildConfig
import com.opdownloader.app.engine.UpdateChecker
import com.opdownloader.app.engine.UpdateResult
import com.opdownloader.app.ui.screens.openLink
import com.opdownloader.app.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

/**
 * A reusable Settings-style row that checks GitHub for a newer release and
 * explains the outcome in a dialog. Safe to drop into any CardSurface column.
 */
@Composable
fun UpdateCheckRow() {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<UpdateResult?>(null) }

    SettingRow(
        title = "Check for updates",
        subtitle = "Current version ${BuildConfig.VERSION_NAME}",
        onClick = {
            if (checking) return@SettingRow
            checking = true
            result = null
            scope.launch {
                result = UpdateChecker.check()
                checking = false
            }
        },
        trailing = {
            if (checking) {
                CircularProgressIndicator(
                    color = colors.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("›", color = colors.textSecondary, fontSize = 20.sp)
            }
        },
    )

    val current = result ?: return
    AlertDialog(
        onDismissRequest = { result = null },
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                when (current) {
                    is UpdateResult.Available -> "Update available"
                    UpdateResult.UpToDate -> "You're up to date"
                    UpdateResult.NoReleases -> "No releases yet"
                    is UpdateResult.Error -> "Couldn't check for updates"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    when (current) {
                        is UpdateResult.Available -> "Version ${(current as UpdateResult.Available).version} is ready to download from GitHub."
                        UpdateResult.UpToDate -> "oP Downloader ${BuildConfig.VERSION_NAME} is the latest release."
                        UpdateResult.NoReleases -> "No public release has been published yet. Check back later."
                        is UpdateResult.Error -> (current as UpdateResult.Error).message
                    },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                if (current is UpdateResult.Available && current.notes.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        current.notes.take(400).let { if (current.notes.length > 400) "$it…" else it },
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }
        },
        confirmButton = {
            when (current) {
                is UpdateResult.Available -> TextButton(
                    onClick = {
                        openLink(context, (current as UpdateResult.Available).url)
                        result = null
                    },
                ) { Text("Open release", color = colors.primary, fontWeight = FontWeight.Bold) }

                is UpdateResult.Error -> TextButton(
                    onClick = {
                        result = null
                        checking = true
                        scope.launch {
                            result = UpdateChecker.check()
                            checking = false
                        }
                    },
                ) { Text("Retry", color = colors.primary, fontWeight = FontWeight.Bold) }

                else -> TextButton(onClick = { result = null }) {
                    Text("OK", color = colors.primary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { result = null }) {
                Text("Close", color = colors.textSecondary)
            }
        },
    )
}
