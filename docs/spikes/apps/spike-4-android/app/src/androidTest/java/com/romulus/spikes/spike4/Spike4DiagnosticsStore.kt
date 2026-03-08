package com.romulus.spikes.spike4

import java.io.File
import kotlinx.serialization.encodeToString

class Spike4DiagnosticsStore(
    private val sessionId: String,
    private val appId: String,
    private val deviceMetadata: DeviceMetadata,
) {
    private val events = mutableListOf<DiagnosticsEvent>()
    private val failures = mutableListOf<FailureEvent>()

    fun record(
        domain: String,
        event: String,
        outcome: String,
        runId: String? = null,
        taskId: String? = null,
        snapshotId: String? = null,
        details: Map<String, String> = emptyMap(),
    ) {
        events += DiagnosticsEvent(
            timestamp = nowUtc(),
            sessionId = sessionId,
            domain = domain,
            event = event,
            outcome = outcome,
            runId = runId,
            taskId = taskId,
            snapshotId = snapshotId,
            details = details,
        )
    }

    fun recordFailure(
        domain: String,
        event: String,
        stage: Spike4Stage,
        errorCode: String,
        message: String,
        runId: String? = null,
        taskId: String? = null,
        details: Map<String, String> = emptyMap(),
    ) {
        failures += FailureEvent(
            timestamp = nowUtc(),
            sessionId = sessionId,
            domain = domain,
            event = event,
            outcome = "failed",
            stage = stage,
            errorCode = errorCode,
            message = message,
            runId = runId,
            taskId = taskId,
            details = details,
        )
    }

    fun events(): List<DiagnosticsEvent> = events.toList()

    fun failures(): List<FailureEvent> = failures.toList()

    fun write(runRoot: File) {
        val diagnosticsRoot = runRoot.resolve("diagnostics").apply { mkdirs() }
        diagnosticsRoot.resolve("manifest.json").writeText(
            spike4Json.encodeToString(
                DiagnosticsManifest(
                    contractVersion = 1,
                    sessionId = sessionId,
                    appId = appId,
                    androidRelease = deviceMetadata.androidRelease,
                    deviceModel = "${deviceMetadata.manufacturer} ${deviceMetadata.model}",
                    exportTimestamp = nowUtc(),
                    redactionPolicyVersion = 1,
                )
            )
        )
        diagnosticsRoot.resolve("timeline.jsonl").writeText(
            events.joinToString(separator = "\n", postfix = "\n") { spike4Json.encodeToString(it) }
        )
        diagnosticsRoot.resolve("failures.jsonl").writeText(
            failures.joinToString(separator = "\n", postfix = if (failures.isEmpty()) "" else "\n") {
                spike4Json.encodeToString(it)
            }
        )
        diagnosticsRoot.resolve("summary.json").writeText(
            spike4Json.encodeToString(
                DiagnosticsSummary(
                    eventCountsByDomain = events.groupingBy { it.domain }.eachCount().toSortedMap(),
                    failureCountsByDomain = failures.groupingBy { it.domain }.eachCount().toSortedMap(),
                    latestFailureByDomain = failures.groupBy { it.domain }.mapValues { (_, items) -> items.last() }.toSortedMap(),
                )
            )
        )
    }
}
