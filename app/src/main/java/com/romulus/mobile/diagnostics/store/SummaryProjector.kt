package com.romulus.mobile.diagnostics.store

import com.romulus.mobile.diagnostics.events.DiagnosticDomain
import com.romulus.mobile.diagnostics.events.DiagnosticEvent
import com.romulus.mobile.diagnostics.events.DiagnosticsQueueSummary
import com.romulus.mobile.diagnostics.events.DiagnosticsSummary
import com.romulus.mobile.diagnostics.events.isFailureOutcome

internal class SummaryProjector {
    fun project(events: List<DiagnosticEvent>): DiagnosticsSummary {
        val eventCounts = mutableMapOf<DiagnosticDomain, Int>()
        val failureCounts = mutableMapOf<DiagnosticDomain, Int>()
        var latestQueueSummary: DiagnosticsQueueSummary? = null
        var latestSourceRefreshOutcome: String? = null
        val latestFailureByDomain = mutableMapOf<DiagnosticDomain, String>()

        events.forEach { event ->
            eventCounts[event.domain] = eventCounts.getOrDefault(event.domain, 0) + 1
            if (event.isFailureOutcome()) {
                failureCounts[event.domain] = failureCounts.getOrDefault(event.domain, 0) + 1
                latestFailureByDomain[event.domain] = event.context["message"]
                    ?: event.context["errorType"]
                    ?: event.event
            }
            if (
                event.context.containsKey("summaryCompleted") &&
                event.context.containsKey("summaryTotal")
            ) {
                latestQueueSummary = DiagnosticsQueueSummary(
                    completed = event.context["summaryCompleted"]?.toIntOrNull() ?: 0,
                    total = event.context["summaryTotal"]?.toIntOrNull() ?: 0,
                    failed = event.context["summaryFailed"]?.toIntOrNull() ?: 0,
                    cancelled = event.context["summaryCancelled"]?.toIntOrNull() ?: 0
                )
            }
            event.context["refreshOutcome"]?.let { refreshOutcome ->
                latestSourceRefreshOutcome = refreshOutcome
            }
        }

        return DiagnosticsSummary(
            eventCountsByDomain = eventCounts.toMap(),
            failureCountsByDomain = failureCounts.toMap(),
            latestQueueSummary = latestQueueSummary,
            latestSourceRefreshOutcome = latestSourceRefreshOutcome,
            latestFailureByDomain = latestFailureByDomain.toMap()
        )
    }
}
