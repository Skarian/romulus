package com.romulus.spikes.spike3

import com.romulus.spikes.spike3.artifacts.ArtifactWriter
import com.romulus.spikes.spike3.model.Spike3CaseDefinitions
import com.romulus.spikes.spike3.runner.Spike3MatrixRunner
import com.romulus.spikes.spike3.server.FixtureHttpServer
import java.util.concurrent.CountDownLatch

private const val DEFAULT_PORT = 8788

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "matrix"
    val paths = Spike3Paths.discover()
    paths.requireFixtureRoot()
    val port = System.getenv("SPIKE3_SERVER_PORT")?.toIntOrNull() ?: DEFAULT_PORT

    when (command) {
        "matrix" -> runMatrix(paths, port)
        "server" -> runServer(paths, port)
        else -> error("Unknown Spike 3 command: $command")
    }
}

private fun runMatrix(paths: Spike3Paths, port: Int) {
    val artifactWriter = ArtifactWriter(paths.artifactRoot)
    val runner = Spike3MatrixRunner(
        cases = Spike3CaseDefinitions.defaultCases(),
        fixtureServer = FixtureHttpServer(paths.fixtureHttpRoot, port),
        artifactWriter = artifactWriter,
    )
    val summary = runner.runAll()
    println("Spike 3 run directory: ${summary.runDirectory}")
    check(summary.allExpectationsMet) { "Spike 3 matrix failed. See ${summary.runDirectory}/summary.md" }
}

private fun runServer(paths: Spike3Paths, port: Int) {
    FixtureHttpServer(paths.fixtureHttpRoot, port).use { server ->
        val baseUri = server.start()
        println("Spike 3 server ready at $baseUri")
        Runtime.getRuntime().addShutdownHook(Thread { server.close() })
        CountDownLatch(1).await()
    }
}
