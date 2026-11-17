package com.opdownloader.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opdownloader.app.data.Store
import com.opdownloader.app.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Space used by the shared external storage volume. */
data class StorageStats(val totalBytes: Long, val freeBytes: Long, val usedBytes: Long)

fun storageStats(): StorageStats = runCatching {
    val stat = StatFs(Environment.getExternalStorageDirectory().absolutePath)
    val total = stat.totalBytes
    val free = stat.availableBytes
    StorageStats(totalBytes = total, freeBytes = free, usedBytes = total - free)
}.getOrDefault(StorageStats(0L, 0L, 0L))

fun formatDate(millis: Long): String =
    if (millis <= 0L) "—"
    else SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

fun formatShortDate(millis: Long): String =
    if (millis <= 0L) "—"
    else SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))

fun readClipboard(context: Context): String = runCatching {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = cm.primaryClip ?: return ""
    clip.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
}.getOrDefault("")

/** Padding used by every tab screen so content clears the floating tab bar. */
val ScreenPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 120.dp)

val ScreenPaddingNoTabs = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp)

@Composable
fun ThemeToggleButton(modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val settings by Store.settings.collectAsState()
    val isDark = settings.themeMode != "light"
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .clickable {
                Store.updateSettings { it.copy(themeMode = if (isDark) "light" else "dark") }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
            contentDescription = "Toggle theme",
            tint = if (isDark) colors.tertiary else colors.primary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val colors = LocalAppColors.current
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint ?: colors.textPrimary,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Spacer(Modifier.size(3.dp))
                Text(subtitle, color = colors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CardSurface(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column {
            content()
        }
    }
}
