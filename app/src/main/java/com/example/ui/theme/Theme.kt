package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulseDarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF0E3A4D),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = EmeraldShield,
    onSecondary = ObsidianBg,
    secondaryContainer = Color(0xFF0A3829),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = ElectricIndigo,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = InkLight,
    surface = ObsidianSurface,
    onSurface = InkLight,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = InkMuted,
    outline = ObsidianBorder,
    error = CrimsonAlert,
    onError = Color.White,
  )

private val PulseLightColorScheme =
  lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF0E4A5C),
    secondary = EmeraldDarkLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = ElectricIndigo,
    onTertiary = Color.White,
    background = AlabasterBg,
    onBackground = DeepNavy,
    surface = AlabasterSurface,
    onSurface = DeepNavy,
    surfaceVariant = AlabasterSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    outline = AlabasterBorder,
    error = CrimsonAlert,
    onError = Color.White,
  )

@Composable
fun PulseVpnTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) PulseDarkColorScheme else PulseLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = PulseTypography,
    content = content,
  )
}
