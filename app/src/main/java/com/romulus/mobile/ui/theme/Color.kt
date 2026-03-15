package com.romulus.mobile.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.romulus.mobile.R

private data class RomulusColorRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color
)

@Composable
fun romulusColorScheme(darkTheme: Boolean): ColorScheme {
    val colors = romulusColorRoles()
    return remember(darkTheme, colors) {
        if (darkTheme) {
            darkRomulusColorScheme(colors)
        } else {
            lightRomulusColorScheme(colors)
        }
    }
}

@Composable
private fun romulusColorRoles(): RomulusColorRoles = RomulusColorRoles(
    primary = colorResource(R.color.romulus_primary),
    onPrimary = colorResource(R.color.romulus_on_primary),
    primaryContainer = colorResource(R.color.romulus_primary_container),
    onPrimaryContainer = colorResource(R.color.romulus_on_primary_container),
    secondary = colorResource(R.color.romulus_secondary),
    onSecondary = colorResource(R.color.romulus_on_secondary),
    secondaryContainer = colorResource(R.color.romulus_secondary_container),
    onSecondaryContainer = colorResource(R.color.romulus_on_secondary_container),
    tertiary = colorResource(R.color.romulus_tertiary),
    onTertiary = colorResource(R.color.romulus_on_tertiary),
    tertiaryContainer = colorResource(R.color.romulus_tertiary_container),
    onTertiaryContainer = colorResource(R.color.romulus_on_tertiary_container),
    error = colorResource(R.color.romulus_error),
    onError = colorResource(R.color.romulus_on_error),
    errorContainer = colorResource(R.color.romulus_error_container),
    onErrorContainer = colorResource(R.color.romulus_on_error_container),
    background = colorResource(R.color.romulus_background),
    onBackground = colorResource(R.color.romulus_on_background),
    surface = colorResource(R.color.romulus_surface),
    onSurface = colorResource(R.color.romulus_on_surface),
    surfaceVariant = colorResource(R.color.romulus_surface_variant),
    onSurfaceVariant = colorResource(R.color.romulus_on_surface_variant),
    outline = colorResource(R.color.romulus_outline)
)

private fun lightRomulusColorScheme(colors: RomulusColorRoles): ColorScheme = lightColorScheme(
    primary = colors.primary,
    onPrimary = colors.onPrimary,
    primaryContainer = colors.primaryContainer,
    onPrimaryContainer = colors.onPrimaryContainer,
    secondary = colors.secondary,
    onSecondary = colors.onSecondary,
    secondaryContainer = colors.secondaryContainer,
    onSecondaryContainer = colors.onSecondaryContainer,
    tertiary = colors.tertiary,
    onTertiary = colors.onTertiary,
    tertiaryContainer = colors.tertiaryContainer,
    onTertiaryContainer = colors.onTertiaryContainer,
    error = colors.error,
    onError = colors.onError,
    errorContainer = colors.errorContainer,
    onErrorContainer = colors.onErrorContainer,
    background = colors.background,
    onBackground = colors.onBackground,
    surface = colors.surface,
    onSurface = colors.onSurface,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.onSurfaceVariant,
    outline = colors.outline
)

private fun darkRomulusColorScheme(colors: RomulusColorRoles): ColorScheme = darkColorScheme(
    primary = colors.primary,
    onPrimary = colors.onPrimary,
    primaryContainer = colors.primaryContainer,
    onPrimaryContainer = colors.onPrimaryContainer,
    secondary = colors.secondary,
    onSecondary = colors.onSecondary,
    secondaryContainer = colors.secondaryContainer,
    onSecondaryContainer = colors.onSecondaryContainer,
    tertiary = colors.tertiary,
    onTertiary = colors.onTertiary,
    tertiaryContainer = colors.tertiaryContainer,
    onTertiaryContainer = colors.onTertiaryContainer,
    error = colors.error,
    onError = colors.onError,
    errorContainer = colors.errorContainer,
    onErrorContainer = colors.onErrorContainer,
    background = colors.background,
    onBackground = colors.onBackground,
    surface = colors.surface,
    onSurface = colors.onSurface,
    surfaceVariant = colors.surfaceVariant,
    onSurfaceVariant = colors.onSurfaceVariant,
    outline = colors.outline
)
