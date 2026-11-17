package com.opdownloader.app.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.opdownloader.app.ui.theme.LocalAppColors

/** True when Android will not put the app to sleep while a download runs. */
fun isBackgroundAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return manager.isIgnoringBatteryOptimizations(context.packageName)
}

/** System dialog that grants the battery-optimization exemption. */
fun backgroundRequestIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
    } else {
        null
    }

/** Opens the system list where the user can re-enable optimization later. */
fun openBackgroundSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        Intent(Settings.ACTION_SETTINGS)
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Settings row for the "run in background" permission. Turning it on opens the
 * Android battery dialog; turning it off opens the system settings because
 * there is no public intent to silently re-enable optimizations. The switch is
 * visual only - the whole row owns the click so the dialog opens exactly once.
 */
@Composable
fun BackgroundPermissionRow() {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(isBackgroundAllowed(context)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed = isBackgroundAllowed(context)
    }

    fun request() {
        val intent = backgroundRequestIntent(context)
        if (intent == null) {
            openBackgroundSettings(context)
            return
        }
        runCatching { launcher.launch(intent) }.onFailure { openBackgroundSettings(context) }
    }

    val subtitle = if (allowed) {
        "Downloads keep running when the screen is off"
    } else {
        "Tap to keep downloads running when the screen is off"
    }

    SettingRow(
        title = "Run in background",
        subtitle = subtitle,
        onClick = { if (allowed) openBackgroundSettings(context) else request() },
        trailing = {
            Switch(
                checked = allowed,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primary,
                    checkedTrackColor = colors.primary.copy(alpha = 0.5f),
                ),
            )
        },
    )
}
