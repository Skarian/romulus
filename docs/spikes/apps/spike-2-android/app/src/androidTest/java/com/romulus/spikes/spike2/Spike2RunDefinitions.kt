package com.romulus.spikes.spike2

object Spike2RunDefinitions {
    private const val hostInputRoot = "docs/spikes/fixtures/generated/spike-2/input"

    fun all(): List<RunDefinition> = listOf(
        RunDefinition(
            id = "run-a-single-level",
            title = "Single-level extraction",
            description = "Extract normal zip, rar, and 7z fixtures plus the unicode zip without recursive unarchive.",
            destinationSubfolder = "run-a-single-level",
            fixtures = listOf(
                FixtureDescriptor(
                    id = "simple-zip",
                    relativeInputPath = "zip/simple.zip",
                    hostInputPath = "$hostInputRoot/zip/simple.zip",
                    archiveFamily = ArchiveFamily.ZIP,
                    outputSubdirectory = "simple-zip",
                ),
                FixtureDescriptor(
                    id = "unicode-zip",
                    relativeInputPath = "zip/unicode_file_names.zip",
                    hostInputPath = "$hostInputRoot/zip/unicode_file_names.zip",
                    archiveFamily = ArchiveFamily.ZIP,
                    outputSubdirectory = "unicode-zip",
                ),
                FixtureDescriptor(
                    id = "simple-7z",
                    relativeInputPath = "7z/simple.7z",
                    hostInputPath = "$hostInputRoot/7z/simple.7z",
                    archiveFamily = ArchiveFamily.SEVEN_ZIP,
                    outputSubdirectory = "simple-7z",
                ),
                FixtureDescriptor(
                    id = "simple-rar",
                    relativeInputPath = "rar/simple.rar",
                    hostInputPath = "$hostInputRoot/rar/simple.rar",
                    archiveFamily = ArchiveFamily.RAR,
                    outputSubdirectory = "simple-rar",
                ),
            ),
            unarchiveEnabled = true,
            recursiveUnarchiveEnabled = false,
            renameRule = null,
            expectedOutcome = RunExpectation.SUCCESS,
        ),
        RunDefinition(
            id = "run-b-nested-no-recursion",
            title = "Nested archive without recursion",
            description = "Extract the nested zip with recursive unarchive disabled so the inner archive remains in the final tree.",
            destinationSubfolder = "run-b-nested-no-recursion",
            fixtures = listOf(
                FixtureDescriptor(
                    id = "nested-no-recursion",
                    relativeInputPath = "nested/nested-archive.zip",
                    hostInputPath = "$hostInputRoot/nested/nested-archive.zip",
                    archiveFamily = ArchiveFamily.ZIP,
                    outputSubdirectory = "nested",
                ),
            ),
            unarchiveEnabled = true,
            recursiveUnarchiveEnabled = false,
            renameRule = null,
            expectedOutcome = RunExpectation.SUCCESS,
        ),
        RunDefinition(
            id = "run-c-nested-recursive-rename",
            title = "Nested archive with recursion and rename",
            description = "Extract the same nested zip with recursion enabled and prove rename applies only to final non-archive outputs.",
            destinationSubfolder = "run-c-nested-recursive-rename",
            fixtures = listOf(
                FixtureDescriptor(
                    id = "nested-recursive",
                    relativeInputPath = "nested/nested-archive.zip",
                    hostInputPath = "$hostInputRoot/nested/nested-archive.zip",
                    archiveFamily = ArchiveFamily.ZIP,
                    outputSubdirectory = "nested",
                ),
            ),
            unarchiveEnabled = true,
            recursiveUnarchiveEnabled = true,
            renameRule = RenameRule(pattern = "test", replacement = "renamed-test"),
            expectedOutcome = RunExpectation.SUCCESS,
        ),
        RunDefinition(
            id = "run-d-corruption-failure",
            title = "Deterministic corruption failure observation",
            description = "Attempt extraction of a run-local intentionally corrupted copy of the simple 7z fixture and capture the deterministic runtime failure envelope.",
            destinationSubfolder = "run-d-corruption-failure",
            fixtures = listOf(
                FixtureDescriptor(
                    id = "corrupt-7z",
                    relativeInputPath = "7z/simple.7z",
                    hostInputPath = "$hostInputRoot/7z/simple.7z",
                    archiveFamily = ArchiveFamily.SEVEN_ZIP,
                    outputSubdirectory = "failure",
                    inputMutation = FixtureInputMutation.REPLACE_WITH_GARBAGE_BYTES,
                ),
            ),
            unarchiveEnabled = true,
            recursiveUnarchiveEnabled = false,
            renameRule = null,
            expectedOutcome = RunExpectation.EXPECTED_FAILURE,
        ),
    )
}
