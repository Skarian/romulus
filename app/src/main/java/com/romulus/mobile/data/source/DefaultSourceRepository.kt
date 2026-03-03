package com.romulus.mobile.data.source

import android.content.Context
import android.net.Uri
import com.romulus.mobile.core.source.SnapshotIdFactory
import com.romulus.mobile.core.time.ClockProvider
import com.romulus.mobile.core.validation.ValidationResult
import com.romulus.mobile.data.settings.SettingsRepository
import com.romulus.mobile.domain.source.RefreshResult
import com.romulus.mobile.domain.source.SourceMode
import com.romulus.mobile.domain.source.SourceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class DefaultSourceRepository(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val clockProvider: ClockProvider,
    private val json: Json = Json { ignoreUnknownKeys = true; prettyPrint = true },
    private val httpClient: OkHttpClient = OkHttpClient()
) : SourceRepository {

    private val entryContractMapper = SourceEntryContractMapper()
    private val snapshotDirectory = File(appContext.filesDir, SNAPSHOT_DIRECTORY).apply { mkdirs() }

    override fun observeActiveSnapshot(): Flow<SourceSnapshot> {
        return settingsRepository.settings.map { settings ->
            val snapshotId = settings.sourceSnapshotId
            if (snapshotId.isNullOrBlank() || settings.sourceMode == null || settings.sourceValue.isNullOrBlank()) {
                emptySnapshot()
            } else {
                readSnapshot(snapshotId) ?: emptySnapshot(
                    sourceMode = settings.sourceMode,
                    sourceValue = settings.sourceValue
                )
            }
        }
    }

    override suspend fun loadActiveSnapshot(): SourceSnapshot {
        val settings = settingsRepository.settings.first()
        val snapshotId = settings.sourceSnapshotId ?: return emptySnapshot()
        return readSnapshot(snapshotId) ?: emptySnapshot(
            sourceMode = settings.sourceMode ?: SourceMode.FILE,
            sourceValue = settings.sourceValue.orEmpty()
        )
    }

    override suspend fun setLocalFileSource(uri: Uri): ValidationResult {
        val content = withContext(Dispatchers.IO) {
            appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } ?: return ValidationResult.Invalid("Unable to read selected JSON file")

        val parseResult = parseAndValidate(
            content = content,
            sourceMode = SourceMode.FILE,
            sourceValue = uri.toString(),
            stale = false
        )

        return if (parseResult is ParseResult.Success) {
            settingsRepository.setSource(SourceMode.FILE, uri.toString(), parseResult.snapshot.snapshotId)
            settingsRepository.setSourceFreshness(isStale = false, lastRefreshEpochMs = parseResult.snapshot.generatedAtEpochMs)
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid((parseResult as ParseResult.Failure).message)
        }
    }

    override suspend fun setUrlSource(url: String): ValidationResult {
        val normalizedUrl = url.trim()
        if (!normalizedUrl.startsWith("http://") && !normalizedUrl.startsWith("https://")) {
            return ValidationResult.Invalid("URL source must start with http:// or https://")
        }
        val responseBody = fetchUrl(normalizedUrl)
            ?: return ValidationResult.Invalid("Could not fetch JSON from URL")

        val parseResult = parseAndValidate(
            content = responseBody,
            sourceMode = SourceMode.URL,
            sourceValue = normalizedUrl,
            stale = false
        )

        return if (parseResult is ParseResult.Success) {
            settingsRepository.setSource(SourceMode.URL, normalizedUrl, parseResult.snapshot.snapshotId)
            settingsRepository.setSourceFreshness(isStale = false, lastRefreshEpochMs = parseResult.snapshot.generatedAtEpochMs)
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid((parseResult as ParseResult.Failure).message)
        }
    }

    override suspend fun refreshFromUrlOnColdLaunchIfNeeded(): RefreshResult {
        val settings = settingsRepository.settings.first()
        if (settings.sourceMode != SourceMode.URL || settings.sourceValue.isNullOrBlank()) {
            return RefreshResult.Failed("No URL source configured", usedCachedSnapshot = false)
        }

        val responseBody = fetchUrl(settings.sourceValue)
        if (responseBody == null) {
            val snapshot = loadActiveSnapshot()
            val usedCached = snapshot.entries.isNotEmpty() || snapshot.issues.isNotEmpty()
            settingsRepository.setSourceFreshness(isStale = usedCached, lastRefreshEpochMs = settings.sourceLastRefreshEpochMs)
            return RefreshResult.Failed(
                message = "Unable to refresh source URL",
                usedCachedSnapshot = usedCached
            )
        }

        val parseResult = parseAndValidate(
            content = responseBody,
            sourceMode = SourceMode.URL,
            sourceValue = settings.sourceValue,
            stale = false
        )

        return if (parseResult is ParseResult.Success) {
            settingsRepository.setSource(SourceMode.URL, settings.sourceValue, parseResult.snapshot.snapshotId)
            settingsRepository.setSourceFreshness(isStale = false, lastRefreshEpochMs = parseResult.snapshot.generatedAtEpochMs)
            RefreshResult.Success(parseResult.snapshot)
        } else {
            settingsRepository.setSourceFreshness(isStale = true, lastRefreshEpochMs = settings.sourceLastRefreshEpochMs)
            RefreshResult.Failed(
                message = (parseResult as ParseResult.Failure).message,
                usedCachedSnapshot = true
            )
        }
    }

    private suspend fun fetchUrl(url: String): String? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use null
                    }
                    response.body?.string()
                }
            }.getOrNull()
        }
    }

    private suspend fun parseAndValidate(
        content: String,
        sourceMode: SourceMode,
        sourceValue: String,
        stale: Boolean
    ): ParseResult {
        val parsed = try {
            json.decodeFromString(SourceDocumentDto.serializer(), content)
        } catch (exception: SerializationException) {
            return ParseResult.Failure("JSON is invalid: ${exception.message}")
        }

        if (parsed.version != 1) {
            return ParseResult.Failure("version must be 1")
        }

        val entryMapping = entryContractMapper.map(parsed.entries)

        val snapshot = SourceSnapshot(
            snapshotId = SnapshotIdFactory.newId(clockProvider.nowEpochMillis()),
            sourceMode = sourceMode,
            sourceValue = sourceValue,
            generatedAtEpochMs = clockProvider.nowEpochMillis(),
            entries = entryMapping.entries,
            issues = entryMapping.issues,
            stale = stale
        )

        writeSnapshot(snapshot)
        return ParseResult.Success(snapshot)
    }

    private suspend fun writeSnapshot(snapshot: SourceSnapshot) {
        withContext(Dispatchers.IO) {
            val file = File(snapshotDirectory, "${snapshot.snapshotId}.json")
            file.writeText(json.encodeToString(snapshot))
        }
    }

    private suspend fun readSnapshot(snapshotId: String): SourceSnapshot? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(snapshotDirectory, "$snapshotId.json")
                if (!file.exists()) return@runCatching null
                json.decodeFromString(SourceSnapshot.serializer(), file.readText())
            }.getOrNull()
        }
    }

    private fun emptySnapshot(
        sourceMode: SourceMode = SourceMode.FILE,
        sourceValue: String = ""
    ): SourceSnapshot {
        return SourceSnapshot(
            snapshotId = "empty",
            sourceMode = sourceMode,
            sourceValue = sourceValue,
            generatedAtEpochMs = clockProvider.nowEpochMillis(),
            entries = emptyList(),
            issues = emptyList(),
            stale = false
        )
    }

    private sealed interface ParseResult {
        data class Success(val snapshot: SourceSnapshot) : ParseResult

        data class Failure(val message: String) : ParseResult
    }

    companion object {
        private const val SNAPSHOT_DIRECTORY = "source_snapshots"
    }
}
