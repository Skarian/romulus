import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val spike2InputDir = rootProject.projectDir.resolve("../../fixtures/generated/spike-2/input").canonicalFile
val spike2ArtifactRoot = rootProject.projectDir.resolve("../../fixtures/generated/spike-2/run-artifacts").canonicalFile
val generatedAndroidTestAssetsRoot = layout.buildDirectory.dir("generated/spike-2/androidTestAssets")
val generatedAndroidTestInputDir = generatedAndroidTestAssetsRoot.map { it.dir("input") }

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

fun Project.requireSpike2DeviceSerial(): String {
    val requestedSerial = providers.environmentVariable("ANDROID_SERIAL").orNull?.trim()?.takeIf { it.isNotEmpty() }
    if (requestedSerial != null) {
        return requestedSerial
    }

    val serials = connectedDeviceSerials()
    if (serials.size != 1) {
        error(
            "Spike 2 requires exactly one connected device or emulator when ANDROID_SERIAL is unset. " +
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

fun String.assertSpike2InstrumentationSucceeded() {
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
        throw GradleException("Spike 2 instrumentation failed. See the instrumentation output above.")
    }
}

android {
    namespace = "com.romulus.spikes.spike2"
    testNamespace = "com.romulus.spikes.spike2.test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.romulus.spikes.spike2"
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
    implementation("com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
}

val syncSpike2AndroidTestAssets = tasks.register<Sync>("syncSpike2AndroidTestAssets") {
    from(spike2InputDir)
    into(generatedAndroidTestInputDir)
    includeEmptyDirs = true
}

tasks.matching { it.name in setOf("preDebugAndroidTestBuild", "mergeDebugAndroidTestAssets") }
    .configureEach {
        dependsOn(syncSpike2AndroidTestAssets)
    }

val clearSpike2HostArtifacts = tasks.register<Delete>("clearSpike2HostArtifacts") {
    delete(
        spike2ArtifactRoot.resolve("latest"),
        spike2ArtifactRoot.resolve("latest.tmp")
    )
}

val spike2RunInstrumentation = tasks.register("spike2RunInstrumentation") {
    group = "verification"
    description = "Install the Spike 2 harness APKs and run the full matrix on one deterministic device."
    dependsOn(syncSpike2AndroidTestAssets, "installDebug", "installDebugAndroidTest")

    doLast {
        val serial = requireSpike2DeviceSerial()
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
                    "com.romulus.spikes.spike2.Spike2MatrixInstrumentedTest",
                    "-e",
                    "spike2_device_serial",
                    serial,
                    "com.romulus.spikes.spike2.test/androidx.test.runner.AndroidJUnitRunner"
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
            throw GradleException("Spike 2 instrumentation adb command failed with exit code ${result.exitValue}.")
        }
        renderedOutput.assertSpike2InstrumentationSucceeded()
    }
}

val pullSpike2Artifacts = tasks.register("pullSpike2Artifacts") {
    group = "verification"
    description = "Pull the latest Spike 2 run artifacts from app-specific external storage back to the host workspace."

    doLast {
        val serial = requireSpike2DeviceSerial()
        val deviceLatestPath = "/sdcard/Android/data/com.romulus.spikes.spike2/files/spike-2/run-artifacts/latest"
        val probeOutput = ByteArrayOutputStream()
        exec {
            isIgnoreExitValue = true
            commandLine(adb(serial, "shell", "ls", deviceLatestPath))
            standardOutput = probeOutput
            errorOutput = probeOutput
        }
        if (probeOutput.toString().contains("No such file or directory")) {
            logger.lifecycle("Spike 2 device artifacts were not present at $deviceLatestPath; nothing was pulled.")
            return@doLast
        }

        val tmpDir = spike2ArtifactRoot.resolve("latest.tmp")
        val latestDir = spike2ArtifactRoot.resolve("latest")
        tmpDir.ensureCleanDirectory()
        spike2ArtifactRoot.mkdirs()

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

tasks.register("spike2ConnectedMatrix") {
    group = "verification"
    description = "Run the Spike 2 Android matrix and pull the resulting artifacts to the host workspace."
    dependsOn(clearSpike2HostArtifacts, spike2RunInstrumentation)
}

spike2RunInstrumentation.configure {
    finalizedBy(pullSpike2Artifacts)
}
