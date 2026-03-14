package com.romulus.mobile.diagnostics.store

internal class RetentionRotator(
    private val diagnosticsStore: DiagnosticsStore,
    private val summaryProjector: SummaryProjector,
    private val maxBytes: Long = MAX_RETAINED_BYTES
) {
    fun enforce(): Result<Unit> {
        return try {
            if (diagnosticsStore.snapshot().getOrThrow().totalBytes <= maxBytes) {
                Result.success(Unit)
            } else {
                val retainedEvents = diagnosticsStore
                    .readTimelineEvents()
                    .getOrThrow()
                    .toMutableList()
                while (retainedEvents.isNotEmpty()) {
                    retainedEvents.removeAt(0)
                    diagnosticsStore.overwriteRetainedEvents(retainedEvents).getOrThrow()
                    diagnosticsStore
                        .writeSummary(summaryProjector.project(retainedEvents))
                        .getOrThrow()
                    if (diagnosticsStore.snapshot().getOrThrow().totalBytes <= maxBytes) {
                        return Result.success(Unit)
                    }
                }
                Result.success(Unit)
            }
        } catch (error: IllegalStateException) {
            Result.failure(error)
        } catch (error: java.io.IOException) {
            Result.failure(error)
        }
    }

    private companion object {
        const val MAX_RETAINED_BYTES = 25L * 1024L * 1024L
    }
}
