package com.romulus.spikes.spike1

import com.romulus.spikes.spike1.config.EnvFileLoader
import com.romulus.spikes.spike1.config.Spike1Config
import com.romulus.spikes.spike1.config.WorkspacePaths
import com.romulus.spikes.spike1.model.KnownUncachedStatus
import com.romulus.spikes.spike1.model.MatrixGateStatus
import com.romulus.spikes.spike1.model.Spike1Profile
import com.romulus.spikes.spike1.service.ArtifactWriter
import com.romulus.spikes.spike1.service.DownloadVerifier
import com.romulus.spikes.spike1.service.GateEvaluator
import com.romulus.spikes.spike1.service.MatrixPlanner
import com.romulus.spikes.spike1.service.RealDebridClient
import com.romulus.spikes.spike1.service.SelectionPlanner
import com.romulus.spikes.spike1.service.Spike1Runner
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) = runBlocking {
    val workspacePaths = WorkspacePaths.discover()
    val invocation = Spike1Invocation.parse(args.toList(), workspacePaths.defaultEnvFile)

    try {
        val envValues = EnvFileLoader().load(invocation.envFile)
        val config = Spike1Config.from(envValues)
        val runner = Spike1Runner(
            client = RealDebridClient.create(config.apiToken),
            matrixPlanner = MatrixPlanner(),
            selectionPlanner = SelectionPlanner(),
            artifactWriter = ArtifactWriter(workspacePaths.artifactRoot),
            gateEvaluator = GateEvaluator(),
            downloadVerifier = DownloadVerifier(OkHttpClient()),
        )
        when (invocation.profile) {
            Spike1Profile.LIKELY_CACHED -> {
                val report = runner.run(invocation.envFile, config)
                report.summary.runOutcomes.sortedBy { it.executionOrder }.forEach { outcome ->
                    println("${outcome.runId}: ${outcome.status}")
                }
                println("matrix gate: ${report.summary.gateStatus}")
                report.summary.blockerCode?.let { blockerCode ->
                    println("blocker: $blockerCode")
                }
                println("artifacts: ${report.invocationDir}")
                if (report.summary.gateStatus != MatrixGateStatus.PASS) {
                    exitProcess(1)
                }
            }
            Spike1Profile.KNOWN_UNCACHED -> {
                val report = runner.runKnownUncached(invocation.envFile, config, workspacePaths)
                println("known-uncached: ${report.summary.status}")
                report.summary.torrentId?.let { println("torrent id: $it") }
                println("selected path: ${report.summary.selectedPath}")
                report.summary.lastObservedStatus?.let { println("last status: $it") }
                report.summary.lastProgress?.let { println("last progress: ${it}%") }
                println("artifacts: ${report.invocationDir}")
                if (report.summary.status == KnownUncachedStatus.TERMINAL_FAILURE) {
                    exitProcess(1)
                }
            }
        }
    } catch (throwable: Throwable) {
        System.err.println(throwable.message ?: throwable::class.qualifiedName.orEmpty())
        exitProcess(1)
    }
}

private data class Spike1Invocation(
    val profile: Spike1Profile,
    val envFile: Path,
) {
    companion object {
        fun parse(args: List<String>, defaultEnvFile: Path): Spike1Invocation {
            var profile = Spike1Profile.LIKELY_CACHED
            var envFile = defaultEnvFile
            var sawEnvFile = false
            args.forEach { arg ->
                when {
                    arg.startsWith("--profile=") -> {
                        profile = when (arg.removePrefix("--profile=")) {
                            "likely-cached" -> Spike1Profile.LIKELY_CACHED
                            "known-uncached" -> Spike1Profile.KNOWN_UNCACHED
                            else -> throw IllegalArgumentException("Unsupported Spike 1 profile: $arg")
                        }
                    }
                    arg.startsWith("--") -> throw IllegalArgumentException("Unsupported Spike 1 argument: $arg")
                    sawEnvFile -> throw IllegalArgumentException("Spike 1 accepts at most one env-file path override")
                    else -> {
                        envFile = Path.of(arg).toAbsolutePath().normalize()
                        sawEnvFile = true
                    }
                }
            }
            return Spike1Invocation(profile = profile, envFile = envFile)
        }
    }
}
