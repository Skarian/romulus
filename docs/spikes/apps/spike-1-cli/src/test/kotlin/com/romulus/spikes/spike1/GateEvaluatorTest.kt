package com.romulus.spikes.spike1

import com.romulus.spikes.spike1.model.MatrixGateStatus
import com.romulus.spikes.spike1.model.RunOutcome
import com.romulus.spikes.spike1.model.RunStatus
import com.romulus.spikes.spike1.model.LinkMappingStatus
import com.romulus.spikes.spike1.service.GateEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals

class GateEvaluatorTest {
    private val evaluator = GateEvaluator()

    @Test
    fun passesWhenRunsABCDPassAndRunEIsExpectedFailure() {
        val summary = evaluator.evaluate(
            startedAt = "2026-03-06T00:00:00Z",
            finishedAt = "2026-03-06T00:01:00Z",
            artifactRoot = "/tmp/spike-1",
            envFile = "/tmp/.env.local",
            runOrder = listOf(
                "run-b-add-directory",
                "run-a-add-root",
                "run-c-add-exact-zip",
                "run-d-selected-only-download",
                "run-e-deterministic-failure",
            ),
            runOutcomes = listOf(
                RunOutcome(
                    runId = "run-b-add-directory",
                    specLabel = "Run B",
                    executionOrder = 1,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(2),
                    providerSelectedFileIdsAfterSelection = listOf(2),
                    observedStatuses = listOf("waiting_files_selection", "downloaded"),
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-a-add-root",
                    specLabel = "Run A",
                    executionOrder = 2,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(1, 2),
                    providerSelectedFileIdsAfterSelection = listOf(1, 2),
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-c-add-exact-zip",
                    specLabel = "Run C",
                    executionOrder = 3,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(4),
                    providerSelectedFileIdsAfterSelection = listOf(4),
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-d-selected-only-download",
                    specLabel = "Run D",
                    executionOrder = 4,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(2),
                    providerSelectedFileIdsAfterSelection = listOf(2),
                    reusedPriorTorrent = true,
                    downloadCount = 1,
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-e-deterministic-failure",
                    specLabel = "Run E",
                    executionOrder = 5,
                    status = RunStatus.EXPECTED_FAILURE,
                    message = "ok",
                ),
            ),
        )

        assertEquals(MatrixGateStatus.PASS, summary.gateStatus)
    }

    @Test
    fun answersExistingTorrentBehaviorFromAddAgainEvidence() {
        val summary = evaluator.evaluate(
            startedAt = "2026-03-06T00:00:00Z",
            finishedAt = "2026-03-06T00:01:00Z",
            artifactRoot = "/tmp/spike-1",
            envFile = "/tmp/.env.local",
            runOrder = listOf(
                "run-b-add-directory",
                "run-a-add-root",
                "run-c-add-exact-zip",
                "run-d-selected-only-download",
                "run-e-deterministic-failure",
            ),
            runOutcomes = listOf(
                RunOutcome(
                    runId = "run-b-add-directory",
                    specLabel = "Run B",
                    executionOrder = 1,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(2),
                    providerSelectedFileIdsAfterSelection = listOf(2),
                    observedStatuses = listOf("waiting_files_selection", "downloaded"),
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-a-add-root",
                    specLabel = "Run A",
                    executionOrder = 2,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(1, 2),
                    providerSelectedFileIdsAfterSelection = listOf(1, 2),
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-c-add-exact-zip",
                    specLabel = "Run C",
                    executionOrder = 3,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(4),
                    providerSelectedFileIdsAfterSelection = listOf(4),
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-d-selected-only-download",
                    specLabel = "Run D",
                    executionOrder = 4,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(4),
                    providerSelectedFileIdsAfterSelection = listOf(4),
                    reusedPriorTorrent = true,
                    downloadCount = 1,
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-e-deterministic-failure",
                    specLabel = "Run E",
                    executionOrder = 5,
                    status = RunStatus.EXPECTED_FAILURE,
                    message = "ok",
                ),
            ),
        )

        assertEquals(MatrixGateStatus.PASS, summary.gateStatus)
        assertEquals("Q8", summary.evidenceQuestions[7].id)
        assertEquals("ANSWERED", summary.evidenceQuestions[7].status.name)
    }

    @Test
    fun treatsSuccessfulCountMismatchAsAnsweredEvidenceInsteadOfGateFailure() {
        val summary = evaluator.evaluate(
            startedAt = "2026-03-06T00:00:00Z",
            finishedAt = "2026-03-06T00:01:00Z",
            artifactRoot = "/tmp/spike-1",
            envFile = "/tmp/.env.local",
            runOrder = listOf(
                "run-b-add-directory",
                "run-a-add-root",
                "run-c-add-exact-zip",
                "run-d-selected-only-download",
                "run-e-deterministic-failure",
            ),
            runOutcomes = listOf(
                RunOutcome(
                    runId = "run-b-add-directory",
                    specLabel = "Run B",
                    executionOrder = 1,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(2, 3),
                    providerSelectedFileIdsAfterSelection = listOf(2, 3),
                    unrestrictCallCount = 1,
                    mismatchedLinkMapping = true,
                    linkMappingStatus = LinkMappingStatus.COUNT_MISMATCH,
                ),
                RunOutcome(
                    runId = "run-a-add-root",
                    specLabel = "Run A",
                    executionOrder = 2,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(1, 2),
                    providerSelectedFileIdsAfterSelection = listOf(1, 2),
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.COUNT_MISMATCH,
                ),
                RunOutcome(
                    runId = "run-c-add-exact-zip",
                    specLabel = "Run C",
                    executionOrder = 3,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(4),
                    providerSelectedFileIdsAfterSelection = listOf(4),
                    unrestrictCallCount = 1,
                    linkMappingStatus = LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER,
                ),
                RunOutcome(
                    runId = "run-d-selected-only-download",
                    specLabel = "Run D",
                    executionOrder = 4,
                    status = RunStatus.PASS,
                    message = "ok",
                    selectedFileIds = listOf(4),
                    providerSelectedFileIdsAfterSelection = listOf(4),
                    reusedPriorTorrent = true,
                    unrestrictCallCount = 2,
                    downloadCount = 2,
                    linkMappingStatus = LinkMappingStatus.COUNT_MISMATCH,
                ),
                RunOutcome(
                    runId = "run-e-deterministic-failure",
                    specLabel = "Run E",
                    executionOrder = 5,
                    status = RunStatus.EXPECTED_FAILURE,
                    message = "ok",
                ),
            ),
        )

        assertEquals(MatrixGateStatus.PASS, summary.gateStatus)
        assertEquals("Q5", summary.evidenceQuestions[4].id)
        assertEquals("ANSWERED", summary.evidenceQuestions[4].status.name)
    }
}
