package com.romulus.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun RomulusTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = romulusColorScheme(darkTheme),
        typography = RomulusTypography,
        shapes = RomulusShapes,
        content = content
    )
}
