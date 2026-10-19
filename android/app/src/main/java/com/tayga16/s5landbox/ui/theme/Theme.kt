package com.tayga16.s5landbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ink = Color(0xFF060A10)
private val surface = Color(0xFF0C1420)
private val surfaceHigh = Color(0xFF131E2E)
private val line = Color(0xFF26374C)
private val blue = Color(0xFF4DA3FF)
private val mint = Color(0xFF7CE0C7)
private val coral = Color(0xFFFF6B6B)
private val text = Color(0xFFE7EEF8)
private val textDim = Color(0xFF93A5BC)

private val scheme = darkColorScheme(
    primary = blue,
    onPrimary = Color(0xFF04101F),
    primaryContainer = Color(0xFF123A5E),
    onPrimaryContainer = Color(0xFFD9EBFF),
    secondary = mint,
    onSecondary = Color(0xFF042019),
    secondaryContainer = Color(0xFF0F3B33),
    onSecondaryContainer = Color(0xFFC9FFF2),
    tertiary = Color(0xFFFFC46B),
    background = ink,
    onBackground = text,
    surface = surface,
    onSurface = text,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = textDim,
    outline = line,
    outlineVariant = Color(0xFF1B2839),
    error = coral,
    onError = Color(0xFF2B0606),
)

private val typography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun S5LAndBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        content = content,
    )
}
