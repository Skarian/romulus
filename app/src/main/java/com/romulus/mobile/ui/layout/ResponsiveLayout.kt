@file:Suppress("MagicNumber", "MatchingDeclarationName")

package com.romulus.mobile.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class ResponsiveMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionSpacing: Dp,
    val contentSpacing: Dp,
    val maxContentWidth: Dp
)

@Composable
fun rememberResponsiveMetrics(): ResponsiveMetrics {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 960 -> ResponsiveMetrics(
            horizontalPadding = 40.dp,
            verticalPadding = 32.dp,
            sectionSpacing = 24.dp,
            contentSpacing = 20.dp,
            maxContentWidth = 900.dp
        )

        widthDp >= 720 -> ResponsiveMetrics(
            horizontalPadding = 32.dp,
            verticalPadding = 24.dp,
            sectionSpacing = 20.dp,
            contentSpacing = 16.dp,
            maxContentWidth = 760.dp
        )

        widthDp >= 520 -> ResponsiveMetrics(
            horizontalPadding = 24.dp,
            verticalPadding = 20.dp,
            sectionSpacing = 16.dp,
            contentSpacing = 14.dp,
            maxContentWidth = 640.dp
        )

        else -> ResponsiveMetrics(
            horizontalPadding = 16.dp,
            verticalPadding = 16.dp,
            sectionSpacing = 12.dp,
            contentSpacing = 10.dp,
            maxContentWidth = 480.dp
        )
    }
}

@Composable
fun ResponsiveScreenContainer(
    modifier: Modifier = Modifier,
    metrics: ResponsiveMetrics = rememberResponsiveMetrics(),
    scrollable: Boolean = false,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(metrics.contentSpacing),
    content: @Composable ColumnScope.(ResponsiveMetrics) -> Unit
) {
    val contentModifier = Modifier
        .fillMaxWidth()
        .widthIn(max = metrics.maxContentWidth)
        .padding(horizontal = metrics.horizontalPadding, vertical = metrics.verticalPadding)
    val scrollModifier = if (scrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = contentModifier.then(scrollModifier),
            verticalArrangement = verticalArrangement
        ) {
            content(metrics)
        }
    }
}
