package com.romulus.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

private val RomulusTypography = Typography()

@Composable
fun RomulusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkRomulusColorScheme
    } else {
        LightRomulusColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = RomulusTypography,
        content = content
    )
}
