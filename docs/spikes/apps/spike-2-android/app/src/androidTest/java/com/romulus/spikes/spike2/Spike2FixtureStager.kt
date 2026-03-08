package com.romulus.spikes.spike2

import android.content.Context
import android.content.res.AssetManager
import java.io.File

class Spike2FixtureStager(
    private val assetManager: AssetManager,
    private val appContext: Context,
) {
    fun stage(sessionId: String, requiredRelativePaths: Set<String>): StagedFixtureWorkspace {
        val sessionRoot = appContext.filesDir.resolve("spike-2/sessions/$sessionId")
        val stagedInputRoot = sessionRoot.resolve("input")
        sessionRoot.deleteRecursively()
        stagedInputRoot.mkdirs()
        copyAssetsRecursively(assetPath = "input", destination = stagedInputRoot)

        requiredRelativePaths.forEach { relativePath ->
            val stagedFile = stagedInputRoot.resolve(relativePath)
            check(stagedFile.exists()) { "Missing staged fixture: $relativePath" }
        }

        return StagedFixtureWorkspace(
            sessionId = sessionId,
            sessionRoot = sessionRoot,
            stagedInputRoot = stagedInputRoot,
        )
    }

    private fun copyAssetsRecursively(assetPath: String, destination: File) {
        val children = assetManager.list(assetPath)?.sorted().orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        destination.mkdirs()
        children.forEach { child ->
            val childAssetPath = if (assetPath.isEmpty()) child else "$assetPath/$child"
            copyAssetsRecursively(childAssetPath, destination.resolve(child))
        }
    }
}
