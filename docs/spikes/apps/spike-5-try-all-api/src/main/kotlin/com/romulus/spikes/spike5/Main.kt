package com.romulus.spikes.spike5

import com.romulus.spikes.spike5.config.EnvFileLoader
import com.romulus.spikes.spike5.config.Spike5Config
import com.romulus.spikes.spike5.config.WorkspacePaths
import com.romulus.spikes.spike5.service.ArtifactWriter
import com.romulus.spikes.spike5.service.RealDebridClient
import com.romulus.spikes.spike5.service.Spike5Runner
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) = runBlocking {
    val workspacePaths = WorkspacePaths.discover()
    val envFile = args.singleOrNull()?.let(java.nio.file.Path::of)?.toAbsolutePath()?.normalize()
        ?: workspacePaths.defaultEnvFile

    try {
        val envValues = EnvFileLoader().load(envFile)
        val config = Spike5Config.from(envValues)
        val report = Spike5Runner(
            client = RealDebridClient.create(config.apiToken),
            artifactWriter = ArtifactWriter(workspacePaths.artifactRoot),
        ).run(envFile, config)
        println("status: ${report.summary.status}")
        println("cached_whole_torrent: ${report.summary.cachedWholeTorrent}")
        println("deleted_match_count: ${report.summary.deletedMatchCount}")
        report.summary.addedTorrentId?.let { println("torrent_id: $it") }
        println("selected_file_path: ${report.summary.selectedFilePath}")
        println("selected_file_found: ${report.summary.selectedFileFound}")
        report.summary.selectedFileId?.let { println("selected_file_id: $it") }
        println("folder_link_count: ${report.summary.folderLinkCount}")
        println("folder_candidate_count: ${report.summary.folderCandidateCount}")
        println("selected_file_downloaded: ${report.summary.selectedFileDownloaded}")
        report.summary.finalStatus?.let { println("final_status: $it") }
        report.summary.finalProgress?.let { println("final_progress: ${it}%") }
        println("artifacts: ${report.invocationDir}")
        if (report.summary.status != "PASS") {
            exitProcess(1)
        }
    } catch (throwable: Throwable) {
        System.err.println(throwable.message ?: throwable::class.qualifiedName.orEmpty())
        exitProcess(1)
    }
}
