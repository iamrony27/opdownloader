package com.opdownloader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val primaryContainer: Color,
    val primaryDeep: Color,
    val onPrimary: Color,
    val onPrimaryDeep: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val tertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val borderSubtle: Color,
    val success: Color,
    val error: Color,
    val warning: Color,
    val info: Color,
    val divider: Color,
    val isDark: Boolean,
)

val DarkAppColors = AppColors(
    background = Color(0xFF101114),
    surface = Color(0xFF191B20),
    surfaceElevated = Color(0xFF22252C),
    surfaceVariant = Color(0xFF2A2D35),
    primary = Color(0xFFB8A4FF),
    primaryContainer = Color(0xFF4F378B),
    primaryDeep = Color(0xFF6C3FD6),
    onPrimary = Color(0xFF23005C),
    onPrimaryDeep = Color(0xFFFFFFFF),
    secondary = Color(0xFF80D6D0),
    secondaryContainer = Color(0xFF004F4D),
    tertiary = Color(0xFFFFB86B),
    textPrimary = Color(0xFFF5F3FA),
    textSecondary = Color(0xFFA7A5B0),
    textMuted = Color(0xFF6E6C78),
    border = Color(0xFF323640),
    borderSubtle = Color(0xFF252830),
    success = Color(0xFF65D6A0),
    error = Color(0xFFFF727A),
    warning = Color(0xFFFFC857),
    info = Color(0xFF64B5F6),
    divider = Color(0xFF282B33),
    isDark = true,
)

val LightAppColors = AppColors(
    background = Color(0xFFF7F5FC),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEDE8F5),
    surfaceVariant = Color(0xFFE4DFED),
    primary = Color(0xFF6750A4),
    primaryContainer = Color(0xFFEADDFF),
    primaryDeep = Color(0xFF5B3E9E),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryDeep = Color(0xFFFFFFFF),
    secondary = Color(0xFF356D69),
    secondaryContainer = Color(0xFFBCECE7),
    tertiary = Color(0xFF87551E),
    textPrimary = Color(0xFF191721),
    textSecondary = Color(0xFF686673),
    textMuted = Color(0xFF9B99A6),
    border = Color(0xFFDDD9E6),
    borderSubtle = Color(0xFFEAE6F2),
    success = Color(0xFF1A8754),
    error = Color(0xFFBA1A1A),
    warning = Color(0xFF8C5B00),
    info = Color(0xFF1976D2),
    divider = Color(0xFFE4E0EC),
    isDark = false,
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

@Composable
fun resolveDark(mode: String): Boolean = when (mode) {
    "light" -> false
    "system" -> isSystemInDarkTheme()
    else -> true
}

@Composable
fun PremiumTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            secondary = colors.secondary,
            secondaryContainer = colors.secondaryContainer,
            tertiary = colors.tertiary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
            outline = colors.border,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer,
            secondary = colors.secondary,
            secondaryContainer = colors.secondaryContainer,
            tertiary = colors.tertiary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceVariant,
            onSurfaceVariant = colors.textSecondary,
            error = colors.error,
            outline = colors.border,
        )
    }

    val typography = Typography(
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold),
    )

    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            content = content,
        )
    }
}
