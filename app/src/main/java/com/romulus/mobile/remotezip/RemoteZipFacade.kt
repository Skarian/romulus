package com.romulus.mobile.remotezip

@Suppress("RedundantSuspendModifier", "UnusedParameter")
class RemoteZipFacade {
    suspend fun enumerate(request: EnumerateRemoteZipRequest): Result<EnumeratedRemoteZip> =
        Result.failure(UnsupportedOperationException("RemoteZipFacade is not wired yet"))

    suspend fun copySelectedEntry(request: CopySelectedEntryRequest): Result<Unit> = Result.failure(
        UnsupportedOperationException("RemoteZipFacade is not wired yet")
    )
}
