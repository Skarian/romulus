package com.romulus.mobile.source.ingest

import android.content.Context
import android.net.Uri
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

@Serializable
internal data class SourceTorrentDocument(
    val url: String,
    @SerialName("partName")
    val partLabel: String? = null
)

@Serializable
internal data class IgnoreRulesDocument(val glob: List<String> = emptyList())

@Serializable
internal data class SourceEntryDocument(
    val displayName: String,
    val subfolder: String,
    val torrents: List<SourceTorrentDocument>,
    val path: String? = null,
    val ignore: IgnoreRulesDocument? = null,
    val rename: RenameRule? = null,
    val unarchive: Boolean? = null,
    val recursiveUnarchive: Boolean? = null
)

@Serializable
internal data class SourceDocument(val version: Int, val entries: List<SourceEntryDocument>)

internal interface SourceLoader {
    suspend fun loadFromUrl(url: String): Result<ByteArray>

    suspend fun loadFromPersistedUri(uri: String): Result<ByteArray>

    suspend fun canReadPersistedUri(uri: String): Result<Unit>
}

internal class SourceDocumentParser(
    private val sourceLoader: SourceLoader,
    private val json: Json,
    private val schemaValidator: SourceSchemaValidator
) {
    suspend fun load(command: AcceptSourceCommand): Result<SourceDocument> {
        val bytes = loadBytes(command).mapCatching { bytes ->
            schemaValidator.validate(bytes).getOrElse { error ->
                throw IllegalStateException(
                    error.message ?: "Source document does not satisfy schema",
                    error
                )
            }
            bytes
        }
        return bytes
            .mapCatching(::parseDocument)
            .mapCatching(::validateDocument)
    }

    suspend fun canReadPersistedUri(uri: String): Result<Unit> =
        sourceLoader.canReadPersistedUri(uri)

    private suspend fun loadBytes(command: AcceptSourceCommand): Result<ByteArray> {
        val bytes = when (command.mode) {
            SourceMode.URL -> loadUrlBytes(command.rawValue)
            SourceMode.FILE -> {
                val persistedUri = command.persistedUri ?: command.rawValue
                sourceLoader.loadFromPersistedUri(persistedUri)
            }
        }

        return bytes.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(
                    IllegalStateException(
                        error.message ?: "Source could not be loaded",
                        error
                    )
                )
            }
        )
    }

    private suspend fun loadUrlBytes(rawValue: String): Result<ByteArray> {
        val normalizedUrl = rawValue.trim()
        return if (
            normalizedUrl.startsWith(HTTP_URL_PREFIX) ||
            normalizedUrl.startsWith(HTTPS_URL_PREFIX)
        ) {
            sourceLoader.loadFromUrl(normalizedUrl)
        } else {
            Result.failure(
                IllegalArgumentException("Source URL must start with http:// or https://")
            )
        }
    }

    private fun parseDocument(bytes: ByteArray): SourceDocument = runCatching {
        json.decodeFromString<SourceDocument>(bytes.toString(StandardCharsets.UTF_8))
    }.getOrElse { error ->
        throw IllegalStateException("Source document could not be parsed", error)
    }

    private fun validateDocument(document: SourceDocument): SourceDocument = document

    private companion object {
        const val HTTP_URL_PREFIX = "http://"
        const val HTTPS_URL_PREFIX = "https://"
    }
}

internal class AndroidSourceLoader(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : SourceLoader {
    override suspend fun loadFromUrl(url: String): Result<ByteArray> = withContext(dispatcher) {
        runCatching {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) {
                    "Source request failed with HTTP ${response.code}"
                }
                response.body?.bytes() ?: error("Source response body was empty")
            }
        }
    }

    override suspend fun loadFromPersistedUri(uri: String): Result<ByteArray> =
        withContext(dispatcher) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    stream.readBytes()
                } ?: error("Source document could not be opened")
            }
        }

    override suspend fun canReadPersistedUri(uri: String): Result<Unit> = withContext(dispatcher) {
        runCatching {
            val stream = context.contentResolver.openInputStream(Uri.parse(uri))
            stream?.use { } ?: error("Source document could not be opened")
            Unit
        }
    }
}
