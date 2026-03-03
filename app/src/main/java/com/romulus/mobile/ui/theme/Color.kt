package com.romulus.mobile.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val LightPrimary = Color(0xFF005C84)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFC5E7FF)
private val LightOnPrimaryContainer = Color(0xFF001E2C)
private val LightSecondary = Color(0xFF1A6262)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFBCECEB)
private val LightOnSecondaryContainer = Color(0xFF002020)
private val LightTertiary = Color(0xFF6B5600)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFF9E287)
private val LightOnTertiaryContainer = Color(0xFF221A00)
private val LightError = Color(0xFFB3261E)
private val LightBackground = Color(0xFFF6F9FC)
private val LightOnBackground = Color(0xFF171C20)
private val LightSurface = Color(0xFFF6F9FC)
private val LightOnSurface = Color(0xFF171C20)
private val LightSurfaceVariant = Color(0xFFD9E4EC)
private val LightOnSurfaceVariant = Color(0xFF3D4950)
private val LightOutline = Color(0xFF6D7A82)

private val DarkPrimary = Color(0xFF84CFFF)
private val DarkOnPrimary = Color(0xFF00344C)
private val DarkPrimaryContainer = Color(0xFF004C6B)
private val DarkOnPrimaryContainer = Color(0xFFC5E7FF)
private val DarkSecondary = Color(0xFFA0D0CF)
private val DarkOnSecondary = Color(0xFF003737)
private val DarkSecondaryContainer = Color(0xFF004F4F)
private val DarkOnSecondaryContainer = Color(0xFFBCECEB)
private val DarkTertiary = Color(0xFFDBC66E)
private val DarkOnTertiary = Color(0xFF392F00)
private val DarkTertiaryContainer = Color(0xFF524500)
private val DarkOnTertiaryContainer = Color(0xFFF9E287)
private val DarkError = Color(0xFFF2B8B5)
private val DarkBackground = Color(0xFF0F1418)
private val DarkOnBackground = Color(0xFFDEE4E9)
private val DarkSurface = Color(0xFF0F1418)
private val DarkOnSurface = Color(0xFFDEE4E9)
private val DarkSurfaceVariant = Color(0xFF3D4950)
private val DarkOnSurfaceVariant = Color(0xFFBDC8D0)
private val DarkOutline = Color(0xFF87939B)

val LightRomulusColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline
)

val DarkRomulusColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline
)
