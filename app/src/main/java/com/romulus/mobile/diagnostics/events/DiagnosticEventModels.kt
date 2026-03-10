package com.romulus.mobile.diagnostics.events

import java.time.Instant

enum class DiagnosticDomain {
    APP_SHELL,
    SOURCE,
    HOME,
    FILES,
    ARCHIVE_SELECTION,
    DOWNLOADS,
    SETTINGS,
    NOTIFICATIONS
}

data class DiagnosticEvent(
    val timestamp: Instant,
    val sessionId: String,
    val domain: DiagnosticDomain,
    val event: String,
    val outcome: String,
    val taskId: String?,
    val message: String?
)

@Suppress("LongParameterList")
data class DiagnosticsManifest(
    val contractVersion: Int,
    val appVersion: String,
    val buildNumber: String,
    val androidVersion: String,
    val deviceModel: String,
    val sessionSeed: String,
    val redactionPolicyVersion: Int,
    val exportedAt: Instant?
)

data class DiagnosticsSummary(
    val eventCountsByDomain: Map<DiagnosticDomain, Int>,
    val failureCountsByDomain: Map<DiagnosticDomain, Int>,
    val latestQueueSummary: DiagnosticsQueueSummary?,
    val latestSourceRefreshOutcome: String?,
    val latestFailureByDomain: Map<DiagnosticDomain, String>
)

data class DiagnosticsQueueSummary(
    val completed: Int,
    val total: Int,
    val failed: Int,
    val cancelled: Int
)
