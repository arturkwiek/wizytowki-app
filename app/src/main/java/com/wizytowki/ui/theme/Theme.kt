package com.wizytowki.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val md_theme_light_primary = Color(0xFF1D76DB)
private val md_theme_light_onPrimary = Color(0xFFFFFFFF)
private val md_theme_light_primaryContainer = Color(0xFFD9E2FF)
private val md_theme_light_onPrimaryContainer = Color(0xFF001452)
private val md_theme_light_secondary = Color(0xFF735B70)
private val md_theme_light_onSecondary = Color(0xFFFFFFFF)
private val md_theme_light_secondaryContainer = Color(0xFFFFD8F3)
private val md_theme_light_onSecondaryContainer = Color(0xFF2B192C)
private val md_theme_light_tertiary = Color(0xFF7D5260)
private val md_theme_light_onTertiary = Color(0xFFFFFFFF)
private val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
private val md_theme_light_onTertiaryContainer = Color(0xFF31111D)
private val md_theme_light_error = Color(0xFFB3261E)
private val md_theme_light_errorContainer = Color(0xFFF9DEDC)
private val md_theme_light_onError = Color(0xFFFFFFFF)
private val md_theme_light_onErrorContainer = Color(0xFF410E0B)
private val md_theme_light_background = Color(0xFFFFFBFE)
private val md_theme_light_onBackground = Color(0xFF1C1B1F)
private val md_theme_light_surface = Color(0xFFFFFBFE)
private val md_theme_light_onSurface = Color(0xFF1C1B1F)
private val md_theme_light_surfaceVariant = Color(0xFFEAE1EC)
private val md_theme_light_onSurfaceVariant = Color(0xFF49454E)
private val md_theme_light_outline = Color(0xFF79747E)
private val md_theme_light_inverseOnSurface = Color(0xFFF4EFF4)
private val md_theme_light_inverseSurface = Color(0xFF313033)
private val md_theme_light_inversePrimary = Color(0xFFD9E2FF)
private val md_theme_light_shadow = Color(0xFF000000)
private val md_theme_light_surfaceTint = Color(0xFF1D76DB)

private val md_theme_dark_primary = Color(0xFFD9E2FF)
private val md_theme_dark_onPrimary = Color(0xFF002E78)
private val md_theme_dark_primaryContainer = Color(0xFF1D5BAC)
private val md_theme_dark_onPrimaryContainer = Color(0xFFD9E2FF)
private val md_theme_dark_secondary = Color(0xFFE1BDD6)
private val md_theme_dark_onSecondary = Color(0xFF442444)
private val md_theme_dark_secondaryContainer = Color(0xFF5D3E5B)
private val md_theme_dark_onSecondaryContainer = Color(0xFFFFD8F3)
private val md_theme_dark_tertiary = Color(0xFFF5B8CE)
private val md_theme_dark_onTertiary = Color(0xFF4A2635)
private val md_theme_dark_tertiaryContainer = Color(0xFF63364C)
private val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
private val md_theme_dark_error = Color(0xFFF2B8B5)
private val md_theme_dark_errorContainer = Color(0xFF8C1D18)
private val md_theme_dark_onError = Color(0xFF601410)
private val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)
private val md_theme_dark_background = Color(0xFF1C1B1F)
private val md_theme_dark_onBackground = Color(0xFFE8E7EB)
private val md_theme_dark_surface = Color(0xFF1C1B1F)
private val md_theme_dark_onSurface = Color(0xFFE8E7EB)
private val md_theme_dark_surfaceVariant = Color(0xFF49454E)
private val md_theme_dark_onSurfaceVariant = Color(0xFFCAC7D0)
private val md_theme_dark_outline = Color(0xFF938F99)
private val md_theme_dark_inverseOnSurface = Color(0xFF1C1B1F)
private val md_theme_dark_inverseSurface = Color(0xFFE8E7EB)
private val md_theme_dark_inversePrimary = Color(0xFF1D76DB)
private val md_theme_dark_shadow = Color(0xFF000000)
private val md_theme_dark_surfaceTint = Color(0xFFD9E2FF)

private val LightColors = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    onError = md_theme_light_onError,
    errorContainer = md_theme_light_errorContainer,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
)

private val DarkColors = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    onError = md_theme_dark_onError,
    errorContainer = md_theme_dark_errorContainer,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
)

@Composable
fun WizytowkiAppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
