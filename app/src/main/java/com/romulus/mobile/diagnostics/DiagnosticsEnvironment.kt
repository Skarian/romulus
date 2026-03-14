package com.romulus.mobile.diagnostics

import android.app.Application
import android.os.Build
import com.romulus.mobile.BuildConfig
import com.romulus.mobile.diagnostics.events.DiagnosticsManifest
import java.time.Clock
import java.time.Instant
import java.util.UUID

internal data class DiagnosticsEnvironment(
    val appVersion: String,
    val buildNumber: String,
    val androidVersion: String,
    val deviceModel: String,
    val sessionSeed: String,
    val redactionPolicyVersion: Int
) {
    fun manifest(exportedAt: Instant?): DiagnosticsManifest = DiagnosticsManifest(
        contractVersion = DIAGNOSTICS_CONTRACT_VERSION,
        appVersion = appVersion,
        buildNumber = buildNumber,
        androidVersion = androidVersion,
        deviceModel = deviceModel,
        sessionSeed = sessionSeed,
        redactionPolicyVersion = redactionPolicyVersion,
        exportedAt = exportedAt
    )

    companion object {
        fun create(application: Application, clock: Clock): DiagnosticsEnvironment {
            val sessionSeedSource = "${clock.instant().toEpochMilli()}-${application.packageName}"
            val sessionSeedBytes = sessionSeedSource.encodeToByteArray()
            return DiagnosticsEnvironment(
                appVersion = BuildConfig.VERSION_NAME,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                androidVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                sessionSeed = UUID.nameUUIDFromBytes(sessionSeedBytes).toString(),
                redactionPolicyVersion = REDACTION_POLICY_VERSION
            )
        }
    }
}

private const val DIAGNOSTICS_CONTRACT_VERSION = 1
private const val REDACTION_POLICY_VERSION = 1
