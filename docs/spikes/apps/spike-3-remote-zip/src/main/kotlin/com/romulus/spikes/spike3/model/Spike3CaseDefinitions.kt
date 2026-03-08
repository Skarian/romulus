package com.romulus.spikes.spike3.model

import com.romulus.spikes.spike3.errors.FailureCodes

object Spike3CaseDefinitions {
    fun defaultCases(): List<Spike3Case> = listOf(
        Spike3Case(
            id = "run-a-regular-enumeration",
            fixtureName = "regular.zip",
            endpointMode = RangeMode.FULL,
            ignoreGlobs = emptyList(),
            selectedEntries = emptyList(),
            expectedOutcome = ExpectedOutcome.Enumerate,
        ),
        Spike3Case(
            id = "run-b-zip64-enumeration",
            fixtureName = "zip64.zip",
            endpointMode = RangeMode.FULL,
            ignoreGlobs = emptyList(),
            selectedEntries = emptyList(),
            expectedOutcome = ExpectedOutcome.Enumerate,
        ),
        Spike3Case(
            id = "run-c-big-directory-enumeration",
            fixtureName = "big_directory.zip",
            endpointMode = RangeMode.FULL,
            ignoreGlobs = emptyList(),
            selectedEntries = emptyList(),
            expectedOutcome = ExpectedOutcome.Enumerate,
        ),
        Spike3Case(
            id = "run-d-selected-only-duplicate",
            fixtureName = "duplicate_entries.zip",
            endpointMode = RangeMode.FULL,
            ignoreGlobs = listOf("*.pdf"),
            selectedEntries = listOf(
                EntryIdentity(
                    fixtureName = "duplicate_entries.zip",
                    entryPath = "sample_text_large.txt",
                    localHeaderOffset = 315,
                    compressedSize = 4114,
                    uncompressedSize = 100669,
                    crc32 = 0x977d3fe8,
                ),
                EntryIdentity(
                    fixtureName = "duplicate_entries.zip",
                    entryPath = "sample_text1.txt",
                    localHeaderOffset = 5450,
                    compressedSize = 269,
                    uncompressedSize = 449,
                    crc32 = 0xd335ee7a,
                ),
            ),
            expectedOutcome = ExpectedOutcome.DownloadSelected,
        ),
        Spike3Case(
            id = "run-e-no-range-failure",
            fixtureName = "regular.zip",
            endpointMode = RangeMode.NONE,
            ignoreGlobs = emptyList(),
            selectedEntries = emptyList(),
            expectedOutcome = ExpectedOutcome.Failure(FailureStage.PROBE, FailureCodes.RANGE_NOT_SUPPORTED),
        ),
        Spike3Case(
            id = "run-f-constrained-range-failure",
            fixtureName = "regular.zip",
            endpointMode = RangeMode.CAPPED,
            ignoreGlobs = emptyList(),
            selectedEntries = emptyList(),
            expectedOutcome = ExpectedOutcome.Failure(FailureStage.ZIP_ENUMERATION, FailureCodes.RANGE_WINDOW_REJECTED),
        ),
        Spike3Case(
            id = "run-g-malformed-failure",
            fixtureName = "malformed.zip",
            endpointMode = RangeMode.FULL,
            ignoreGlobs = emptyList(),
            selectedEntries = emptyList(),
            expectedOutcome = ExpectedOutcome.Failure(FailureStage.ZIP_ENUMERATION, FailureCodes.INVALID_ZIP),
        ),
        Spike3Case(
            id = "run-h-encrypted-failure",
            fixtureName = "strong_encrypted.zip",
            endpointMode = RangeMode.FULL,
            ignoreGlobs = emptyList(),
            selectedEntries = listOf(
                EntryIdentity(
                    fixtureName = "strong_encrypted.zip",
                    entryPath = "test.txt",
                    localHeaderOffset = 0,
                    compressedSize = 326,
                    uncompressedSize = 6,
                    crc32 = 0x0972d361,
                ),
            ),
            expectedOutcome = ExpectedOutcome.Failure(FailureStage.DOWNLOAD, FailureCodes.UNSUPPORTED_ENCRYPTION),
        ),
    )
}
