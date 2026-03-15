package com.romulus.mobile.source.ingest

import com.romulus.mobile.source.snapshot.SnapshotPublisher
import java.time.Clock

internal class SourceAcceptanceService(
    private val parser: SourceDocumentParser,
    private val validation: SourceValidation,
    private val sourceConfigStore: SourceConfigStore,
    private val snapshotPublisher: SnapshotPublisher,
    private val clock: Clock
) {
    suspend fun accept(command: AcceptSourceCommand): AcceptSourceResult {
        val document = parser.load(command).getOrElse { error ->
            return AcceptSourceResult.Failed(error.message ?: "Source could not be loaded")
        }
        return acceptLoadedDocument(command, document)
    }

    private suspend fun acceptLoadedDocument(
        command: AcceptSourceCommand,
        document: SourceDocument
    ): AcceptSourceResult {
        val issues = validation.validate(document)
        return if (issues.isNotEmpty()) {
            AcceptSourceResult.Rejected(issues)
        } else {
            stageAcceptedDocument(command, document)
        }
    }

    private suspend fun stageAcceptedDocument(
        command: AcceptSourceCommand,
        document: SourceDocument
    ): AcceptSourceResult {
        val acceptedAt = clock.instant()
        val stagedSnapshotResult = snapshotPublisher.stageReplacement(
            document = document,
            acceptedAt = acceptedAt
        )
        val stagedSnapshot = stagedSnapshotResult.getOrElse { error ->
            return AcceptSourceResult.Failed(error.message ?: "Snapshot staging failed")
        }
        return stageAcceptedConfig(command, acceptedAt, stagedSnapshot)
    }

    private suspend fun stageAcceptedConfig(
        command: AcceptSourceCommand,
        acceptedAt: java.time.Instant,
        stagedSnapshot: com.romulus.mobile.source.snapshot.StagedSnapshot
    ): AcceptSourceResult {
        val stagedConfigResult = sourceConfigStore.stageCandidate(
            AcceptedSourceConfigRecord(
                mode = command.mode,
                rawValue = command.rawValue.trim(),
                persistedUri = command.persistedUri,
                acceptedAt = acceptedAt,
                lastRefreshAt = null,
                lastRefreshOutcome = null
            )
        )
        val stagedConfig = stagedConfigResult.getOrElse { error ->
            snapshotPublisher.discard(stagedSnapshot)
            return AcceptSourceResult.Failed(
                error.message ?: "Accepted source record could not be staged"
            )
        }
        return activateAcceptedSource(acceptedAt, stagedSnapshot, stagedConfig)
    }

    private suspend fun activateAcceptedSource(
        acceptedAt: java.time.Instant,
        stagedSnapshot: com.romulus.mobile.source.snapshot.StagedSnapshot,
        stagedConfig: StagedSourceConfig
    ): AcceptSourceResult {
        val activation = snapshotPublisher.activate(
            stagedSnapshot = stagedSnapshot,
            stagedConfig = stagedConfig,
            activatedAt = acceptedAt
        )
        return if (activation.isFailure) {
            val configCleanup = sourceConfigStore.discardCandidate(stagedConfig)
            val snapshotCleanup = snapshotPublisher.discard(stagedSnapshot)
            if (configCleanup.isFailure || snapshotCleanup.isFailure) {
                AcceptSourceResult.Failed(
                    "Source activation failed and staged cleanup also failed"
                )
            } else {
                AcceptSourceResult.Failed("Source activation failed")
            }
        } else {
            AcceptSourceResult.Accepted(stagedSnapshot.snapshotId)
        }
    }
}
