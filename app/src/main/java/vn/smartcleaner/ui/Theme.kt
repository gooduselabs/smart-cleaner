package vn.smartcleaner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import vn.smartcleaner.data.ThemeMode

object Brand {
    val Teal = Color(0xFF14B8A6); val Indigo = Color(0xFF6366F1); val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFF43F5E); val Sky = Color(0xFF38BDF8); val Lime = Color(0xFF84CC16); val Slate = Color(0xFF64748B)
}

private val Dark = darkColorScheme(
    primary = Brand.Teal, onPrimary = Color(0xFF00201C), primaryContainer = Color(0xFF0F3B37), onPrimaryContainer = Color(0xFFB2F5EA),
    secondary = Brand.Indigo, onSecondary = Color.White, secondaryContainer = Color(0xFF26285A), onSecondaryContainer = Color(0xFFDDE0FF),
    tertiary = Brand.Amber, error = Brand.Rose,
    background = Color(0xFF0A0E17), onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF0A0E17), onSurface = Color(0xFFE6EAF2),
    surfaceContainerLowest = Color(0xFF070A11), surfaceContainerLow = Color(0xFF10151F), surfaceContainer = Color(0xFF151B27),
    surfaceContainerHigh = Color(0xFF1B2230), surfaceContainerHighest = Color(0xFF222A3A),
    surfaceVariant = Color(0xFF1B2230), onSurfaceVariant = Color(0xFF9AA4B8), outline = Color(0xFF3A4458), outlineVariant = Color(0xFF262E3E),
)
private val Light = lightColorScheme(
    primary = Color(0xFF0D9488), onPrimary = Color.White, primaryContainer = Color(0xFFCCFBF1), onPrimaryContainer = Color(0xFF053B36),
    secondary = Color(0xFF4F46E5), onSecondary = Color.White, secondaryContainer = Color(0xFFE0E7FF), onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFFD97706), error = Color(0xFFE11D48),
    background = Color(0xFFF6F8FB), onBackground = Color(0xFF0F172A), surface = Color(0xFFF6F8FB), onSurface = Color(0xFF0F172A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainer = Color(0xFFEEF2F7),
    surfaceContainerHigh = Color(0xFFE7ECF3), surfaceContainerHighest = Color(0xFFDEE4EE),
    surfaceVariant = Color(0xFFE7ECF3), onSurfaceVariant = Color(0xFF5B6476), outline = Color(0xFFB8C1D1), outlineVariant = Color(0xFFDCE2EB),
)

private val T = Typography()
private val AppType = Typography(
    displaySmall = T.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = T.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = T.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = T.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = T.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = T.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppType, shapes = Shapes(), content = content)
}
