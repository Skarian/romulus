package com.romulus.spikes.spike1.service

import com.romulus.spikes.spike1.model.EvidenceAnswer
import com.romulus.spikes.spike1.model.EvidenceStatus
import com.romulus.spikes.spike1.model.LinkMappingStatus
import com.romulus.spikes.spike1.model.MatrixGateStatus
import com.romulus.spikes.spike1.model.MatrixSummary
import com.romulus.spikes.spike1.model.RunOutcome
import com.romulus.spikes.spike1.model.RunStatus

class GateEvaluator {
    fun evaluate(
        startedAt: String,
        finishedAt: String,
        artifactRoot: String,
        envFile: String,
        runOrder: List<String>,
        runOutcomes: List<RunOutcome>,
        blockerCode: String? = null,
        blockerMessage: String? = null,
    ): MatrixSummary {
        val gateStatus = when {
            blockerCode != null -> MatrixGateStatus.BLOCKED
            runOutcomes.size < 5 -> MatrixGateStatus.FAIL
            runOutcomes.any { outcome ->
                when (outcome.runId) {
                    "run-e-deterministic-failure" -> outcome.status != RunStatus.EXPECTED_FAILURE
                    else -> outcome.status != RunStatus.PASS
                }
            } -> MatrixGateStatus.FAIL
            else -> MatrixGateStatus.PASS
        }

        return MatrixSummary(
            startedAt = startedAt,
            finishedAt = finishedAt,
            gateStatus = gateStatus,
            blockerCode = blockerCode,
            blockerMessage = blockerMessage,
            artifactRoot = artifactRoot,
            envFile = envFile,
            runOrder = runOrder,
            runOutcomes = runOutcomes.sortedBy { it.executionOrder },
            evidenceQuestions = buildEvidence(runOutcomes, blockerCode, blockerMessage),
        )
    }

    private fun buildEvidence(
        runOutcomes: List<RunOutcome>,
        blockerCode: String?,
        blockerMessage: String?,
    ): List<EvidenceAnswer> {
        val byId = runOutcomes.associateBy { it.runId }
        if (blockerCode != null) {
            return listOf(
                EvidenceAnswer(
                    id = "Q0",
                    question = "Was the spike blocked before add flow?",
                    status = EvidenceStatus.UNCONFIRMED,
                    answer = blockerMessage ?: blockerCode,
                ),
            )
        }

        val runB = byId["run-b-add-directory"]
        val runA = byId["run-a-add-root"]
        val runC = byId["run-c-add-exact-zip"]
        val runD = byId["run-d-selected-only-download"]
        val runE = byId["run-e-deterministic-failure"]
        val transientObserved = runOutcomes.any { it.transientFailureObserved }
        val observedStates = runOutcomes.flatMap { it.observedStatuses }.distinct()
        val nonFailureRuns = runOutcomes.filter { it.runId != "run-e-deterministic-failure" }
        val mismatchObserved = nonFailureRuns.any { it.linkMappingStatus == LinkMappingStatus.COUNT_MISMATCH }
        val exactCountObserved = nonFailureRuns.any { it.linkMappingStatus == LinkMappingStatus.EXACT_COUNT_PROVIDER_ORDER }

        return listOf(
            EvidenceAnswer(
                id = "Q1",
                question = "What endpoint sequence and state transitions are required from add or add-again or reuse to downloadable link?",
                status = if (runB?.status == RunStatus.PASS && runD?.status == RunStatus.PASS && observedStates.isNotEmpty() && nonFailureRuns.any { it.unrestrictCallCount > 0 }) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = if (runB?.status == RunStatus.PASS && runD?.status == RunStatus.PASS && observedStates.isNotEmpty() && nonFailureRuns.any { it.unrestrictCallCount > 0 }) {
                    "Add, add-again, and downstream reuse completed through info, selectFiles, follow-up info polling, returned restricted links, and unrestrict. Observed statuses: ${observedStates.joinToString()}"
                } else {
                    "Add/add-again/reuse evidence is incomplete."
                },
            ),
            EvidenceAnswer(
                id = "Q2",
                question = "Which file identity fields remain stable across polling and are safe for selection mapping?",
                status = if (nonFailureRuns.any { it.status == RunStatus.PASS && it.providerSelectedFileIdsAfterSelection == it.selectedFileIds }) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = if (nonFailureRuns.any { it.status == RunStatus.PASS && it.providerSelectedFileIdsAfterSelection == it.selectedFileIds }) {
                    "Provider files[].path and files[].id were stable enough to build the selection payload and verify provider-selected IDs after polling. Returned links were treated as downstream link units rather than file identity."
                } else {
                    "No successful selection run proved stable path/id mapping."
                },
            ),
            EvidenceAnswer(
                id = "Q3",
                question = "What happens when the torrent already exists in the user account but the desired files differ from the current selection?",
                status = if (runA?.status == RunStatus.PASS && runB != null && runA.selectedFileIds != runB.selectedFileIds) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = if (runA?.status == RunStatus.PASS && runB != null && runA.selectedFileIds != runB.selectedFileIds) {
                    val sameTorrentId = runA.torrentId != null && runA.torrentId == runB.torrentId
                    "Run A added the same magnet again while the Run B torrent already existed. The returned torrent id was ${if (sameTorrentId) "the same as" else "different from"} Run B, and the provider-selected IDs matched the new explicit selection."
                } else {
                    "Add-again with changed selection was not proven."
                },
            ),
            EvidenceAnswer(
                id = "Q4",
                question = "How does provider behavior change when only a subset of file IDs is submitted?",
                status = if (runB?.status == RunStatus.PASS && !runB.selectionUsedAllLiteral && runB.providerSelectedFileIdsAfterSelection == runB.selectedFileIds) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = if (runB?.status == RunStatus.PASS && !runB.selectionUsedAllLiteral && runB.providerSelectedFileIdsAfterSelection == runB.selectedFileIds) {
                    "Subset selection used explicit provider file IDs, not files=all, and the provider-selected ID set matched the requested selection after polling."
                } else {
                    "Subset selection evidence is missing."
                },
            ),
            EvidenceAnswer(
                id = "Q5",
                question = "How do returned links relate to the verified selected provider file set, and what mismatch patterns appear?",
                status = if (mismatchObserved || exactCountObserved) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = when {
                    mismatchObserved && exactCountObserved ->
                        "Some successful runs had equal selected-file/link counts, while others showed count mismatch. The harness records both cases and treats returned links as downstream units once selection is verified."
                    mismatchObserved ->
                        "At least one successful run showed selected-file/link count mismatch. The harness captured the mismatch and continued by operating on returned links directly."
                    exactCountObserved ->
                        "Successful runs observed equal selected-file/link counts. Provider-order mapping was recorded as an observation, not a guaranteed identity join."
                    else ->
                        "Link mapping evidence is missing."
                },
            ),
            EvidenceAnswer(
                id = "Q6",
                question = "Does local path-scope filtering produce the correct provider file-ID selection payload for root, directory, and exact .zip inputs?",
                status = if (runA?.status == RunStatus.PASS && runB?.status == RunStatus.PASS && runC?.status == RunStatus.PASS) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = if (runA?.status == RunStatus.PASS && runB?.status == RunStatus.PASS && runC?.status == RunStatus.PASS) {
                    "Root, directory, and exact-path selection all resolved to explicit provider file IDs."
                } else {
                    "At least one scope proof is missing."
                },
            ),
            EvidenceAnswer(
                id = "Q7",
                question = "What terminal failure looks cleanly deterministic, and what transient failure is naturally observed if any?",
                status = when {
                    runE?.status == RunStatus.EXPECTED_FAILURE && transientObserved -> EvidenceStatus.ANSWERED
                    runE?.status == RunStatus.EXPECTED_FAILURE -> EvidenceStatus.UNOBSERVED
                    else -> EvidenceStatus.UNCONFIRMED
                },
                answer = when {
                    runE?.status == RunStatus.EXPECTED_FAILURE && transientObserved -> "Run E captured the expected invalid-magnet failure, and a transient failure was also observed during a normal run."
                    runE?.status == RunStatus.EXPECTED_FAILURE -> "Run E captured the expected invalid-magnet failure. No transient failure was observed during normal runs."
                    else -> "Deterministic failure evidence is missing."
                },
            ),
            EvidenceAnswer(
                id = "Q8",
                question = "How should the flow behave when matching torrents for the same magnet hash already exist in the account?",
                status = if (runA?.status == RunStatus.PASS) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = if (runA?.status == RunStatus.PASS) {
                    "Matching torrents in the account do not block execution. Changed selection is modeled by adding the magnet again to create a fresh provider torrent with a new selected state."
                } else {
                    "Behavior with existing matching torrents was not proven."
                },
            ),
            EvidenceAnswer(
                id = "Q9",
                question = "Was selected-only download proof present?",
                status = if (runD?.status == RunStatus.PASS && runD.providerSelectedFileIdsAfterSelection == runD.selectedFileIds && runD.downloadCount == runD.unrestrictCallCount && runD.downloadCount > 0) EvidenceStatus.ANSWERED else EvidenceStatus.UNCONFIRMED,
                answer = if (runD?.status == RunStatus.PASS && runD.providerSelectedFileIdsAfterSelection == runD.selectedFileIds && runD.downloadCount == runD.unrestrictCallCount && runD.downloadCount > 0) {
                    "Run D proved the requested exact-path selection, then unrestrict-called and downloaded only the links returned from that verified selected state."
                } else {
                    "Selected-only download proof is missing."
                },
            ),
        )
    }
}
