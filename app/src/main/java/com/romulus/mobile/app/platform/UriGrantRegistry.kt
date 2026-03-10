package com.romulus.mobile.app.platform

import android.net.Uri
import java.time.Instant

enum class UriGrantKind {
    SOURCE_READ,
    OUTPUT_TREE
}

data class PersistedUriGrant(val kind: UriGrantKind, val uri: Uri, val persistedAt: Instant)

data class GrantRestoreReport(val restored: List<PersistedUriGrant>, val failed: List<Uri>)

interface UriGrantRegistry {
    suspend fun captureSourceGrant(uri: Uri): Result<PersistedUriGrant>

    suspend fun captureOutputGrant(uri: Uri): Result<PersistedUriGrant>

    suspend fun restorePersistedGrants(): GrantRestoreReport

    suspend fun revoke(uri: Uri): Result<Unit>
}
