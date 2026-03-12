package com.romulus.mobile.app.platform

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.Instant

internal class AndroidUriGrantRegistry(context: Context) : UriGrantRegistry {
    private val contentResolver: ContentResolver = context.contentResolver
    private val sharedPreferences = context.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun captureSourceGrant(uri: Uri): Result<PersistedUriGrant> = capture(
        uri = uri,
        kind = UriGrantKind.SOURCE_READ,
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    )

    override suspend fun captureOutputGrant(uri: Uri): Result<PersistedUriGrant> = capture(
        uri = uri,
        kind = UriGrantKind.OUTPUT_TREE,
        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )

    override suspend fun restorePersistedGrants(): GrantRestoreReport {
        val restored = mutableListOf<PersistedUriGrant>()
        val failed = mutableListOf<Uri>()

        readRecord(UriGrantKind.SOURCE_READ)?.let { record ->
            if (hasPersistedPermission(record)) {
                restored += record
            } else {
                failed += record.uri
            }
        }
        readRecord(UriGrantKind.OUTPUT_TREE)?.let { record ->
            if (hasPersistedPermission(record)) {
                restored += record
            } else {
                failed += record.uri
            }
        }

        return GrantRestoreReport(
            restored = restored,
            failed = failed
        )
    }

    override suspend fun revoke(uri: Uri): Result<Unit> = runCatching {
        val sourceRecord = readRecord(UriGrantKind.SOURCE_READ)
        val outputRecord = readRecord(UriGrantKind.OUTPUT_TREE)
        if (sourceRecord?.uri == uri) {
            releasePersistedPermission(sourceRecord)
            clearRecord(UriGrantKind.SOURCE_READ)
        }
        if (outputRecord?.uri == uri) {
            releasePersistedPermission(outputRecord)
            clearRecord(UriGrantKind.OUTPUT_TREE)
        }
        Unit
    }

    private fun capture(uri: Uri, kind: UriGrantKind, flags: Int): Result<PersistedUriGrant> =
        runCatching {
            readRecord(kind)
                ?.takeIf { it.uri != uri }
                ?.let { existing ->
                    releasePersistedPermission(existing)
                    clearRecord(kind)
                }
            contentResolver.takePersistableUriPermission(uri, flags)
            val record = PersistedUriGrant(
                kind = kind,
                uri = uri,
                persistedAt = Instant.now()
            )
            writeRecord(record)
            record
        }

    private fun hasPersistedPermission(record: PersistedUriGrant): Boolean =
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == record.uri &&
                permission.isReadPermission &&
                (record.kind == UriGrantKind.SOURCE_READ || permission.isWritePermission)
        }

    private fun releasePersistedPermission(record: PersistedUriGrant) {
        runCatching {
            contentResolver.releasePersistableUriPermission(record.uri, record.kind.flags)
        }
    }

    private fun readRecord(kind: UriGrantKind): PersistedUriGrant? {
        val uri = sharedPreferences.getString(kind.uriKey, null)?.let(Uri::parse) ?: return null
        val persistedAtEpochMillis = sharedPreferences.getLong(kind.persistedAtKey, 0L)
        val persistedAt = persistedAtEpochMillis
            .takeIf { epochMillis -> epochMillis > 0L }
            ?.let(Instant::ofEpochMilli)
            ?: Instant.EPOCH
        return PersistedUriGrant(
            kind = kind,
            uri = uri,
            persistedAt = persistedAt
        )
    }

    private fun writeRecord(record: PersistedUriGrant) {
        val editor = sharedPreferences.edit()
        editor.putString(record.kind.uriKey, record.uri.toString())
        editor.putLong(record.kind.persistedAtKey, record.persistedAt.toEpochMilli())
        check(editor.commit()) { "${record.kind} grant could not be saved" }
    }

    private fun clearRecord(kind: UriGrantKind) {
        val editor = sharedPreferences.edit()
        editor.remove(kind.uriKey)
        editor.remove(kind.persistedAtKey)
        check(editor.commit()) { "$kind grant could not be removed" }
    }

    private val UriGrantKind.flags: Int
        get() = when (this) {
            UriGrantKind.SOURCE_READ -> Intent.FLAG_GRANT_READ_URI_PERMISSION
            UriGrantKind.OUTPUT_TREE ->
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        }

    private val UriGrantKind.uriKey: String
        get() = when (this) {
            UriGrantKind.SOURCE_READ -> KEY_SOURCE_URI
            UriGrantKind.OUTPUT_TREE -> KEY_OUTPUT_URI
        }

    private val UriGrantKind.persistedAtKey: String
        get() = when (this) {
            UriGrantKind.SOURCE_READ -> KEY_SOURCE_PERSISTED_AT_EPOCH_MILLIS
            UriGrantKind.OUTPUT_TREE -> KEY_OUTPUT_PERSISTED_AT_EPOCH_MILLIS
        }

    private companion object {
        const val PREFERENCES_NAME = "app_uri_grant_registry"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_SOURCE_PERSISTED_AT_EPOCH_MILLIS = "source_persisted_at_epoch_millis"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_PERSISTED_AT_EPOCH_MILLIS = "output_persisted_at_epoch_millis"
    }
}
