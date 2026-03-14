package com.romulus.mobile.diagnostics.events

import com.romulus.mobile.diagnostics.InstantAsEpochMilliSerializer
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class DiagnosticDomain {
    APP_SHELL,
    SOURCE,
    HOME,
    FILES,
    ARCHIVE_SELECTION,
    DOWNLOADS,
    SETTINGS,
    NOTIFICATIONS,
    REAL_DEBRID
}

@Serializable
data class DiagnosticEvent(
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val timestamp: Instant,
    val sessionId: String,
    val domain: DiagnosticDomain,
    val event: String,
    val outcome: String,
    val taskId: String?,
    val snapshotId: String?,
    val context: Map<String, String>
)

@Serializable
@Suppress("LongParameterList")
data class DiagnosticsManifest(
    val contractVersion: Int,
    val appVersion: String,
    val buildNumber: String,
    val androidVersion: String,
    val deviceModel: String,
    val sessionSeed: String,
    val redactionPolicyVersion: Int,
    @Serializable(with = InstantAsEpochMilliSerializer::class)
    val exportedAt: Instant?
)

@Serializable
data class DiagnosticsSummary(
    val eventCountsByDomain: Map<DiagnosticDomain, Int>,
    val failureCountsByDomain: Map<DiagnosticDomain, Int>,
    val latestQueueSummary: DiagnosticsQueueSummary?,
    val latestSourceRefreshOutcome: String?,
    val latestFailureByDomain: Map<DiagnosticDomain, String>
)

@Serializable
data class DiagnosticsQueueSummary(
    val completed: Int,
    val total: Int,
    val failed: Int,
    val cancelled: Int
)

internal fun DiagnosticEvent.isFailureOutcome(): Boolean = outcome in setOf(
    "failed",
    "failure",
    "rejected",
    "timeout"
)
