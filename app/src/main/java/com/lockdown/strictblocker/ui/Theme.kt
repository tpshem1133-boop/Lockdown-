package com.lockdown.strictblocker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Obsidian Vault theme.
 * Deep black + charcoal + pure white, one accent: Electric Lime.
 * Type: Inter-style grotesque (system sans) for UI, monospace for the PIN
 * vault — swap in bundled Inter / JetBrains Mono TTFs in res/font and point
 * these families at them if desired; the layout is designed around them.
 */
val VaultBlack = Color(0xFF0A0A0A)
val Charcoal = Color(0xFF1C1C1E)
val CharcoalLight = Color(0xFF2A2A2E)
val ElectricLime = Color(0xFFCCFF00)
val PureWhite = Color(0xFFFFFFFF)
val MutedWhite = Color(0xFF9A9A9E)

val UiFont = FontFamily.SansSerif
val MonoFont = FontFamily.Monospace

private val VaultScheme = darkColorScheme(
    primary = ElectricLime,
    onPrimary = VaultBlack,
    background = VaultBlack,
    onBackground = PureWhite,
    surface = Charcoal,
    onSurface = PureWhite,
    surfaceVariant = CharcoalLight,
    onSurfaceVariant = MutedWhite,
    outline = CharcoalLight,
    error = PureWhite,
    onError = VaultBlack
)

private val VaultTypography = Typography(
    displayLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 26.sp),
    titleLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = UiFont, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = UiFont, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = UiFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
)

@Composable
fun LockdownTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VaultScheme,
        typography = VaultTypography,
        content = content
    )
}
