package com.romulus.mobile.downloads.attempts

import com.romulus.mobile.realdebrid.ArchiveContainerLocator
import com.romulus.mobile.remotezip.CopySelectedEntryRequest
import com.romulus.mobile.remotezip.RemoteZipFacade
import com.romulus.mobile.source.browse.ArchiveContainerPreparationService
import com.romulus.mobile.source.browse.ArchivePreparationKey

internal interface ArchiveContainerGateway {
    suspend fun resolveReadyArchiveContainer(
        preparationKey: ArchivePreparationKey
    ): Result<ArchiveContainerLocator>
}

internal class SharedArchiveContainerGateway(
    private val preparationService: ArchiveContainerPreparationService
) : ArchiveContainerGateway {
    override suspend fun resolveReadyArchiveContainer(
        preparationKey: ArchivePreparationKey
    ): Result<ArchiveContainerLocator> = preparationService.resolveReadyContainer(preparationKey)
}

internal interface RemoteZipCopyGateway {
    suspend fun copySelectedEntry(request: CopySelectedEntryRequest): Result<Unit>
}

internal class FacadeRemoteZipCopyGateway(private val facade: RemoteZipFacade) :
    RemoteZipCopyGateway {
    override suspend fun copySelectedEntry(request: CopySelectedEntryRequest): Result<Unit> =
        facade.copySelectedEntry(request)
}
