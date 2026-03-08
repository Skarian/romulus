package com.romulus.spikes.spike3.runner

import com.romulus.spikes.spike3.artifacts.ArtifactWriter
import com.romulus.spikes.spike3.errors.toCaseFailure
import com.romulus.spikes.spike3.filter.IgnoreGlobFilter
import com.romulus.spikes.spike3.http.HttpTraceRecorder
import com.romulus.spikes.spike3.http.OkHttpRangeClient
import com.romulus.spikes.spike3.model.CaseFailure
import com.romulus.spikes.spike3.model.CaseResult
import com.romulus.spikes.spike3.model.CaseStatus
import com.romulus.spikes.spike3.model.ExpectedOutcome
import com.romulus.spikes.spike3.model.RunSummary
import com.romulus.spikes.spike3.model.Spike3Case
import com.romulus.spikes.spike3.server.FixtureHttpServer
import com.romulus.spikes.spike3.zip.RemoteZipSession
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.format.DateTimeFormatter

class Spike3MatrixRunner(
    private val cases: List<Spike3Case>,
    private val fixtureServer: FixtureHttpServer,
    private val artifactWriter: ArtifactWriter,
) {
    fun runAll(): RunSummary {
        val runId = RUN_ID_FORMATTER.format(Instant.now())
        val runDirectory = artifactWriter.openRun(runId)
        val traceRecorder = HttpTraceRecorder()
        val results = mutableListOf<CaseResult>()
        val baseUri = fixtureServer.start()

        try {
            val client = OkHttpRangeClient(recorder = traceRecorder)
            for (case in cases) {
                results += runCase(case, baseUri.toString(), client, runDirectory)
            }
        } finally {
            fixtureServer.close()
            artifactWriter.writeHttpTrace(runDirectory, traceRecorder.snapshot())
            artifactWriter.writeSummary(runDirectory, results, traceRecorder.snapshot())
        }

        return RunSummary(runId = runId, runDirectory = runDirectory, results = results)
    }

    private fun runCase(
        spikeCase: Spike3Case,
        baseUri: String,
        client: OkHttpRangeClient,
        runDirectory: java.nio.file.Path,
    ): CaseResult {
        val archiveUrl = "$baseUri${spikeCase.endpointMode.pathSegment}/${spikeCase.fixtureName}".toHttpUrl()
        var candidateWritten = false
        return try {
            RemoteZipSession(
                caseId = spikeCase.id,
                fixtureName = spikeCase.fixtureName,
                archiveUrl = archiveUrl,
                rangeHttpClient = client,
            ).use { session ->
                val allEntries = session.enumerate()
                val filteredEntries = IgnoreGlobFilter.apply(allEntries, spikeCase.ignoreGlobs, spikeCase.selectedEntries)
                artifactWriter.writeCandidateSet(runDirectory, spikeCase.id, allEntries, filteredEntries, spikeCase.ignoreGlobs)
                candidateWritten = true
                val downloadedFiles = if (spikeCase.selectedEntries.isNotEmpty()) {
                    session.copySelected(
                        spikeCase.selectedEntries,
                        runDirectory.resolve("cases").resolve(spikeCase.id).resolve("downloads"),
                    )
                } else {
                    emptyList()
                }
                if (downloadedFiles.isNotEmpty()) {
                    artifactWriter.writeDownloadManifest(runDirectory, spikeCase.id, filteredEntries, downloadedFiles)
                }
                when (spikeCase.expectedOutcome) {
                    ExpectedOutcome.Enumerate, ExpectedOutcome.DownloadSelected -> CaseResult(
                        case = spikeCase,
                        status = CaseStatus.PASSED,
                        matchedExpectation = true,
                        candidateWritten = candidateWritten,
                        downloadedFiles = downloadedFiles,
                        failure = null,
                    )

                    is ExpectedOutcome.Failure -> {
                        val failure = CaseFailure(
                            caseId = spikeCase.id,
                            stage = spikeCase.expectedOutcome.stage,
                            errorCode = "UNEXPECTED_SUCCESS",
                            message = "Expected failure but case completed successfully",
                            causeClass = null,
                        )
                        artifactWriter.writeFailure(runDirectory, spikeCase.id, spikeCase.fixtureName, archiveUrl.toString(), failure)
                        CaseResult(
                            case = spikeCase,
                            status = CaseStatus.FAILED,
                            matchedExpectation = false,
                            candidateWritten = candidateWritten,
                            downloadedFiles = downloadedFiles,
                            failure = failure,
                        )
                    }
                }
            }
        } catch (failure: Throwable) {
            val caseFailure = failure.toCaseFailure(spikeCase.id, fallbackStage = com.romulus.spikes.spike3.model.FailureStage.ZIP_ENUMERATION)
            artifactWriter.writeFailure(runDirectory, spikeCase.id, spikeCase.fixtureName, archiveUrl.toString(), caseFailure)
            val matchedExpectation = matchesExpectedFailure(spikeCase.expectedOutcome, caseFailure)
            CaseResult(
                case = spikeCase,
                status = if (matchedExpectation) CaseStatus.PASSED else CaseStatus.FAILED,
                matchedExpectation = matchedExpectation,
                candidateWritten = candidateWritten,
                downloadedFiles = emptyList(),
                failure = caseFailure,
            )
        }
    }

    private fun matchesExpectedFailure(expectedOutcome: ExpectedOutcome, failure: CaseFailure): Boolean {
        return expectedOutcome is ExpectedOutcome.Failure &&
            expectedOutcome.stage == failure.stage &&
            expectedOutcome.errorCode == failure.errorCode
    }

    private companion object {
        val RUN_ID_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
            .withZone(java.time.ZoneOffset.UTC)
    }
}
