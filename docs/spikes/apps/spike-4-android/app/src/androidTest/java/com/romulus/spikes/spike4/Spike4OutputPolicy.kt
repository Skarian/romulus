package com.romulus.spikes.spike4

import java.io.File

class Spike4OutputPolicy(
    private val runRoot: File,
) {
    private val reservedPaths = linkedSetOf<String>()

    fun resolveTarget(
        outputRoot: File,
        archivePath: String?,
        renameRule: RenameRule?,
        isArchiveCandidate: Boolean,
    ): OutputTarget {
        require(!archivePath.isNullOrBlank()) { "Archive item path is missing" }
        val suspicious = isSuspiciousPath(archivePath)
        if (suspicious) {
            throw IllegalArgumentException("Unsafe archive path: $archivePath")
        }

        val leafName = archivePath.replace('\\', '/').substringAfterLast('/')
        val renameApplied = renameRule != null && !isArchiveCandidate
        val transformedName = if (renameApplied) {
            Regex(renameRule.pattern).replace(leafName, renameRule.replacement)
        } else {
            leafName
        }.ifBlank { leafName }

        val chosenFile = reserveCollisionSafeFile(outputRoot, transformedName)
        return OutputTarget(
            file = chosenFile.file,
            outputRelativePath = chosenFile.file.invariantRelativeTo(runRoot),
            renameApplied = renameApplied,
            collisionIndex = chosenFile.collisionIndex,
            pathRejected = false,
        )
    }

    fun isArchiveCandidate(path: String?): Boolean {
        if (path.isNullOrBlank()) {
            return false
        }
        val lower = path.lowercase()
        return lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")
    }

    private fun isSuspiciousPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (normalized.startsWith("/") || normalized.startsWith("..") || normalized.contains(":/")) {
            return true
        }
        return normalized.split('/').any { it == ".." }
    }

    private fun reserveCollisionSafeFile(outputRoot: File, requestedName: String): ReservedFile {
        outputRoot.mkdirs()
        val baseName = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', "")
        var collisionIndex: Int? = null
        var candidate = outputRoot.resolve(requestedName)
        var suffix = 1
        while (candidate.exists() || reservedPaths.contains(candidate.absolutePath)) {
            collisionIndex = suffix
            val nextName = if (extension.isEmpty() || requestedName.endsWith('.')) {
                "$baseName ($suffix)"
            } else {
                "$baseName ($suffix).$extension"
            }
            candidate = outputRoot.resolve(nextName)
            suffix += 1
        }
        candidate.parentFile?.mkdirs()
        reservedPaths += candidate.absolutePath
        return ReservedFile(candidate, collisionIndex)
    }

    private data class ReservedFile(
        val file: File,
        val collisionIndex: Int?,
    )
}

data class OutputTarget(
    val file: File,
    val outputRelativePath: String,
    val renameApplied: Boolean,
    val collisionIndex: Int?,
    val pathRejected: Boolean,
)
