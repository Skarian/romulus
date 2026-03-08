import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val envFile = rootProject.projectDir.resolve("../../.env.local").canonicalFile
val spike4ArtifactRoot = rootProject.projectDir.resolve("../../fixtures/generated/spike-4/run-artifacts").canonicalFile
val generatedAndroidTestAssetsRoot = layout.buildDirectory.dir("generated/spike-4/androidTestAssets")
val generatedAndroidTestConfigDir = generatedAndroidTestAssetsRoot.map { it.dir("config") }

fun Project.connectedDeviceSerials(): List<String> {
    val output = ByteArrayOutputStream()
    exec {
        commandLine("adb", "devices")
        standardOutput = output
    }
    return output
        .toString()
        .lineSequence()
        .drop(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 2 && parts[1] == "device") {
                parts[0]
            } else {
                null
            }
        }
        .toList()
}

fun Project.requireSpike4DeviceSerial(): String {
    val requestedSerial = providers.environmentVariable("ANDROID_SERIAL").orNull?.trim()?.takeIf { it.isNotEmpty() }
    if (requestedSerial != null) {
        return requestedSerial
    }

    val serials = connectedDeviceSerials()
    if (serials.size != 1) {
        error(
            "Spike 4 requires exactly one connected device or emulator when ANDROID_SERIAL is unset. " +
                "Found ${serials.size}: ${serials.joinToString(", ")}"
        )
    }
    return serials.single()
}

fun Project.adb(serial: String, vararg args: String): List<String> = listOf("adb", "-s", serial) + args

fun File.ensureCleanDirectory() {
    deleteRecursively()
    mkdirs()
}

fun String.assertSpike4InstrumentationSucceeded() {
    val normalized = replace("\r\n", "\n")
    val failureMarkers = listOf(
        "FAILURES!!!",
        "INSTRUMENTATION_STATUS_CODE: -2",
        "Process crashed.",
        "INSTRUMENTATION_FAILED:",
    )
    val hasFailureMarker = failureMarkers.any { normalized.contains(it) }
    val hasSuccessMarker = Regex("""(?m)^OK \(\d+ test[s]?\)$""").containsMatchIn(normalized)
    if (hasFailureMarker || !hasSuccessMarker) {
        throw GradleException("Spike 4 instrumentation failed. See the instrumentation output above.")
    }
}

fun parseEnvFile(file: File): Map<String, String> {
    if (!file.exists()) {
        throw GradleException("Spike 4 runtime config requires ${file.path}. Run `cd docs/spikes && just init-env` first.")
    }
    return file.readLines().mapNotNull { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            null
        } else {
            val delimiterIndex = line.indexOf('=')
            if (delimiterIndex <= 0) {
                throw GradleException("Malformed .env entry: $line")
            }
            val key = line.substring(0, delimiterIndex).trim()
            var value = line.substring(delimiterIndex + 1).trim()
            if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
                value = value.substring(1, value.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
            key to value
        }
    }.toMap()
}

fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

fun envBoolean(values: Map<String, String>, key: String): String {
    val raw = values[key]?.trim()?.lowercase()
    return when (raw) {
        "true", "false" -> raw
        else -> throw GradleException("Spike 4 config field $key must be true or false.")
    }
}

fun envRequired(values: Map<String, String>, key: String): String {
    return values[key]?.trim().orEmpty().takeIf { it.isNotEmpty() }
        ?: throw GradleException("Spike 4 config field $key must be set in ${envFile.path}.")
}

fun envCsv(values: Map<String, String>, key: String): List<String> {
    return values[key]
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
}

android {
    namespace = "com.romulus.spikes.spike4"
    testNamespace = "com.romulus.spikes.spike4.test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.romulus.spikes.spike4"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        disable += "NewerVersionAvailable"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir(generatedAndroidTestAssetsRoot)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    androidTestImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    androidTestImplementation("com.squareup.retrofit2:retrofit:2.11.0")
    androidTestImplementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    androidTestImplementation("org.apache.commons:commons-compress:1.28.0")
    androidTestImplementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")
}

val generateSpike4RuntimeConfig = tasks.register("generateSpike4RuntimeConfig") {
    outputs.dir(generatedAndroidTestAssetsRoot)

    doLast {
        val values = parseEnvFile(envFile)
        val payload = buildString {
            append("{\n")
            append("  \"rdApiToken\": ${jsonString(envRequired(values, "RD_API_TOKEN"))},\n")
            append("  \"magnet\": ${jsonString(envRequired(values, "SPIKE4_MAGNET"))},\n")
            append("  \"torrentPath\": ${jsonString(values["SPIKE4_TORRENT_PATH"]?.trim().orEmpty())},\n")
            append("  \"exactZipPath\": ${jsonString(envRequired(values, "SPIKE4_EXACT_ZIP_PATH"))},\n")
            append("  \"selectedInternalPaths\": [\n")
            append(
                envCsv(values, "SPIKE4_SELECTED_INTERNAL_PATHS")
                    .joinToString(",\n") { "    ${jsonString(it)}" }
            )
            append("\n  ],\n")
            append("  \"ignoreGlobs\": [\n")
            append(
                envCsv(values, "SPIKE4_IGNORE_GLOBS")
                    .joinToString(",\n") { "    ${jsonString(it)}" }
            )
            append("\n  ],\n")
            append("  \"unarchive\": ${envBoolean(values, "SPIKE4_UNARCHIVE")},\n")
            append("  \"destinationSubfolder\": ${jsonString(values["SPIKE4_DESTINATION_SUBFOLDER"]?.trim().orEmpty())},\n")
            append("  \"renamePattern\": ${jsonString(values["SPIKE4_RENAME_PATTERN"]?.trim().orEmpty())},\n")
            append("  \"renameReplacement\": ${jsonString(values["SPIKE4_RENAME_REPLACEMENT"] ?: "")}\n")
            append("}\n")
        }
        val configDir = generatedAndroidTestConfigDir.get().asFile
        configDir.mkdirs()
        configDir.resolve("runtime-config.json").writeText(payload)
    }
}

tasks.matching { it.name in setOf("preDebugAndroidTestBuild", "mergeDebugAndroidTestAssets") }
    .configureEach {
        dependsOn(generateSpike4RuntimeConfig)
    }

val clearSpike4HostArtifacts = tasks.register<Delete>("clearSpike4HostArtifacts") {
    delete(
        spike4ArtifactRoot.resolve("latest"),
        spike4ArtifactRoot.resolve("latest.tmp")
    )
}

val spike4RunInstrumentation = tasks.register("spike4RunInstrumentation") {
    group = "verification"
    description = "Install the Spike 4 harness APKs and run the full connected matrix on one deterministic device."
    dependsOn(generateSpike4RuntimeConfig, "installDebug", "installDebugAndroidTest")

    doLast {
        val serial = requireSpike4DeviceSerial()
        val instrumentationOutput = ByteArrayOutputStream()
        val result = exec {
            isIgnoreExitValue = true
            commandLine(
                adb(
                    serial,
                    "shell",
                    "am",
                    "instrument",
                    "-w",
                    "-r",
                    "-e",
                    "class",
                    "com.romulus.spikes.spike4.Spike4MatrixInstrumentedTest",
                    "-e",
                    "spike4_device_serial",
                    serial,
                    "com.romulus.spikes.spike4.test/androidx.test.runner.AndroidJUnitRunner"
                )
            )
            standardOutput = instrumentationOutput
            errorOutput = instrumentationOutput
        }
        val renderedOutput = instrumentationOutput.toString()
        if (renderedOutput.isNotEmpty()) {
            print(renderedOutput)
        }
        if (result.exitValue != 0) {
            throw GradleException("Spike 4 instrumentation adb command failed with exit code ${result.exitValue}.")
        }
        renderedOutput.assertSpike4InstrumentationSucceeded()
    }
}

val pullSpike4Artifacts = tasks.register("pullSpike4Artifacts") {
    group = "verification"
    description = "Pull the latest Spike 4 run artifacts from app-specific external storage back to the host workspace."

    doLast {
        val serial = requireSpike4DeviceSerial()
        val deviceLatestPath = "/sdcard/Android/data/com.romulus.spikes.spike4/files/spike-4/run-artifacts/latest"
        val probeOutput = ByteArrayOutputStream()
        exec {
            isIgnoreExitValue = true
            commandLine(adb(serial, "shell", "ls", deviceLatestPath))
            standardOutput = probeOutput
            errorOutput = probeOutput
        }
        if (probeOutput.toString().contains("No such file or directory")) {
            logger.lifecycle("Spike 4 device artifacts were not present at $deviceLatestPath; nothing was pulled.")
            return@doLast
        }

        val tmpDir = spike4ArtifactRoot.resolve("latest.tmp")
        val latestDir = spike4ArtifactRoot.resolve("latest")
        tmpDir.ensureCleanDirectory()
        spike4ArtifactRoot.mkdirs()

        exec {
            commandLine(adb(serial, "pull", "$deviceLatestPath/.", tmpDir.absolutePath))
        }

        latestDir.deleteRecursively()
        if (!tmpDir.renameTo(latestDir)) {
            latestDir.ensureCleanDirectory()
            tmpDir.copyRecursively(latestDir, overwrite = true)
            tmpDir.deleteRecursively()
        }
    }
}

tasks.register("spike4ConnectedMatrix") {
    group = "verification"
    description = "Run the full Spike 4 connected matrix and pull the latest artifacts back to the host workspace."
    dependsOn(clearSpike4HostArtifacts, spike4RunInstrumentation)
    finalizedBy(pullSpike4Artifacts)
}
