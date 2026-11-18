package com.opdownloader.app.ui.components

import android.content.Context
import android.content.Intent
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

/**
 * Opens the system battery-optimization list. The user picks oP Downloader and
 * chooses "Don't optimize". This needs no permission — the old
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS dialog required
 * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, a known Play Protect risk signal for
 * apps installed outside Google Play. The foreground download service already
 * keeps transfers alive; this setting only helps on aggressive ROMs.
 */
fun backgroundSettingsIntent(context: Context): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    } else {
        null
    }

/** Opens the system list where the user can change or re-enable optimization. */
fun openBackgroundSettings(context: Context) {
    val intent = backgroundSettingsIntent(context) ?: Intent(Settings.ACTION_SETTINGS)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Settings row for background execution. Both directions open the system
 * battery-optimization list; there is no public intent to change the setting
 * silently. The switch is visual only - the whole row owns the click.
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
        val intent = backgroundSettingsIntent(context)
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
