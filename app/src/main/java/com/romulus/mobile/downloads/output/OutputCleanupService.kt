@file:Suppress("ChainMethodContinuation", "ClassSignature", "ReturnCount")

package com.romulus.mobile.downloads.output

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class OutputCleanupScope(
    val reservation: OutputReservation?,
    val finalOutputs: List<FinalOutputRecord>
)

internal class OutputCleanupService(private val outputFilesystem: OutputFilesystem) {
    suspend fun cleanupForRestart(scopes: List<OutputCleanupScope>): Result<Unit> {
        scopes.filterNot { scope -> scope.isEmpty() }.forEach { scope ->
            cleanupScope(scope).getOrElse { throwable ->
                return Result.failure(throwable)
            }
        }
        return Result.success(Unit)
    }

    private suspend fun cleanupScope(scope: OutputCleanupScope): Result<Unit> {
        val reservation = scope.reservation
        if (reservation != null) {
            val relativePaths = buildSet {
                addAll(scope.finalOutputs.map(FinalOutputRecord::relativePath))
                reservation.directOutput?.let { directOutput ->
                    add(directOutput.relativePath)
                }
                reservation.extractionPlan.forEach { output ->
                    add(output.relativePath)
                }
            }
            relativePaths.forEach { relativePath ->
                outputFilesystem.deleteFinalOutput(
                    outputDirectoryUri = reservation.boundOutputDirectoryUri,
                    relativePath = relativePath
                ).getOrElse { throwable ->
                    return Result.failure(throwable)
                }
            }
            deleteLocalPath(reservation.tempArtifactPath).getOrElse { throwable ->
                return Result.failure(throwable)
            }
            deleteLocalPath(reservation.extractionRootPath).getOrElse { throwable ->
                return Result.failure(throwable)
            }
        } else if (scope.finalOutputs.isNotEmpty()) {
            return Result.failure(
                IllegalStateException("Cannot clean final outputs without a reservation binding")
            )
        }
        return Result.success(Unit)
    }

    private fun OutputCleanupScope.isEmpty(): Boolean =
        reservation == null && finalOutputs.isEmpty()

    private fun deleteLocalPath(path: String): Result<Unit> = runCatching {
        val target = File(path)
        if (!target.exists()) {
            Unit
        } else if (target.isDirectory) {
            check(target.deleteRecursively()) { "Cleanup could not delete $path" }
        } else {
            check(target.delete()) { "Cleanup could not delete $path" }
        }
    }
}
