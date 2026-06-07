package com.example.heicconverter.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme =
  lightColorScheme(
    primary = Azure,
    secondary = Mint,
    tertiary = Lavender,
    background = SurfaceSoft,
    surface = Color.White,
    surfaceContainer = Color(0xFFE9EEFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAF8F5),
    surfaceContainerHighest = Color(0xFFE7F0FF),
    onPrimary = Color.White,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Color(0xFF516076),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
  )

private val DarkScheme =
  darkColorScheme(
    primary = Sky,
    secondary = Mint,
    tertiary = Lavender,
    background = Color(0xFF11131A),
    surface = Color(0xFF181C25),
    surfaceContainer = Color(0xFF1E2432),
    surfaceContainerLow = Color(0xFF1A202B),
    surfaceContainerHigh = Color(0xFF24303B),
    surfaceContainerHighest = Color(0xFF253449),
    onPrimary = Ink,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Color(0xFFF0F3FA),
    onSurface = Color(0xFFF0F3FA),
    onSurfaceVariant = Color(0xFFB8C2D3),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
  )

@Composable
fun MyApplicationTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkScheme
      else -> LightScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
