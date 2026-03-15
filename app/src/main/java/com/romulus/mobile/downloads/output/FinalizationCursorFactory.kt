package com.romulus.mobile.downloads.output

import com.romulus.mobile.downloads.queue.DirectSaveStage
import com.romulus.mobile.downloads.queue.FinalizationCursor
import com.romulus.mobile.downloads.queue.PendingArchivePassCursor

internal fun createInitialFinalizationCursor(
    reservation: OutputReservation,
    artifactPath: String = reservation.tempArtifactPath
): FinalizationCursor = when (reservation.artifact.handling) {
    ReservedArtifactHandling.DIRECT_SAVE ->
        FinalizationCursor.DirectSave(DirectSaveStage.PROMOTING)

    ReservedArtifactHandling.LOCAL_UNARCHIVE ->
        FinalizationCursor.Unarchive(
            pendingPasses = listOf(
                PendingArchivePassCursor(
                    archiveFilePath = artifactPath,
                    lineage = "",
                    passIndex = 1
                )
            ),
            activePassReservedEntries = emptyList(),
            currentPromotionOutputId = null,
            completionPending = false
        )
}
