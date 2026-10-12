package com.emilio.deutschdrill.ui.theme

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
import com.emilio.deutschdrill.data.ThemeMode

/** Colors for answer feedback, readable in both themes. */
object FeedbackColors {
    val correctDark = Color(0xFF7BD88F)
    val wrongDark = Color(0xFFFF8A80)
    val correctLight = Color(0xFF1B7F3B)
    val wrongLight = Color(0xFFC62828)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC94D),
    onPrimary = Color(0xFF2B2000),
    secondary = Color(0xFF9CCAFF),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5900),
    secondary = Color(0xFF00629E),
)

@Composable
fun DrillTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminanceIsDark()

private fun Color.luminanceIsDark(): Boolean = (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
