package com.romulus.spikes.spike2

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.impl.VolumedArchiveInStream

class SevenZipArchiveExtractor {
    fun captureRuntimeInit(): RuntimeInitRecord {
        val versionResult = runCatching { SevenZip.getSevenZipVersion() }
        val version = versionResult.getOrNull()
        val initializationException = runCatching { SevenZip.getLastInitializationException() }.getOrNull()
        return RuntimeInitRecord(
            capturedAtUtc = nowUtc(),
            initializedSuccessfully = runCatching { SevenZip.isInitializedSuccessfully() }.getOrDefault(false),
            sevenZipVersion = version?.version,
            sevenZipVersionDisplay = version?.let { "${it.major}.${it.minor}.${it.build} (${it.date})" },
            sevenZipJBindingVersion = runCatching { SevenZip.getSevenZipJBindingVersion() }.getOrNull(),
            usedPlatform = runCatching { SevenZip.getUsedPlatform() }.getOrNull(),
            lastInitializationException = initializationException?.renderSummary(),
            initializationProbeError = versionResult.exceptionOrNull()?.renderSummary(),
        )
    }

    fun extract(
        runDefinition: RunDefinition,
        archiveInput: PassArchiveInput,
        passId: String,
        runRoot: File,
        finalOutputRoot: File,
        outputPolicy: Spike2OutputPolicy,
    ): ExtractionResult {
        val volumeRecords = mutableListOf<VolumeResolutionRecord>()
        val itemRecords = mutableListOf<ArchiveItemRecord>()
        val outputEntries = mutableListOf<OutputManifestEntry>()

        var archive: IInArchive? = null
        var randomAccessFile: RandomAccessFile? = null
        var sevenZipVolumeCallback: RecordingSevenZipVolumeCallback? = null
        var rarVolumeCallback: RecordingRarVolumeCallback? = null
        var callback: ExtractionCallback? = null
        var failure: FailureRecord? = null

        try {
            archive = when {
                archiveInput.fixture.multipart && archiveInput.fixture.archiveFamily == ArchiveFamily.SEVEN_ZIP -> {
                    sevenZipVolumeCallback = RecordingSevenZipVolumeCallback(
                        fixtureId = archiveInput.fixture.id,
                        firstVolume = archiveInput.sourceFile,
                        records = volumeRecords,
                    )
                    val stream = VolumedArchiveInStream(archiveInput.sourceFile.absolutePath, sevenZipVolumeCallback)
                    SevenZip.openInArchive(ArchiveFormat.SEVEN_ZIP, stream, RecordingOpenCallback())
                }
                archiveInput.fixture.multipart && archiveInput.fixture.archiveFamily == ArchiveFamily.RAR -> {
                    rarVolumeCallback = RecordingRarVolumeCallback(
                        fixtureId = archiveInput.fixture.id,
                        firstVolume = archiveInput.sourceFile,
                        records = volumeRecords,
                    )
                    val firstStream = rarVolumeCallback.getStream(archiveInput.sourceFile.absolutePath)
                        ?: error("Unable to open first multipart RAR volume ${archiveInput.sourceFile}")
                    SevenZip.openInArchive(ArchiveFormat.RAR, firstStream, rarVolumeCallback)
                }
                else -> {
                    randomAccessFile = RandomAccessFile(archiveInput.sourceFile, "r")
                    SevenZip.openInArchive(
                        archiveInput.fixture.archiveFamily.toArchiveFormat(),
                        RandomAccessFileInStream(randomAccessFile),
                        RecordingOpenCallback(),
                    )
                }
            }

            callback = ExtractionCallback(
                archive = archive,
                archiveInput = archiveInput,
                passId = passId,
                runRoot = runRoot,
                outputRoot = finalOutputRoot.resolve(archiveInput.fixture.outputSubdirectory),
                renameRule = runDefinition.renameRule,
                outputPolicy = outputPolicy,
            )

            val indices = mutableListOf<Int>()
            repeat(archive.numberOfItems) { index ->
                val archivePath = archive.getProperty(index, PropID.PATH) as? String
                val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
                if (!isFolder) {
                    indices += index
                }
                itemRecords += ArchiveItemRecord(
                    fixtureId = archiveInput.fixture.id,
                    archiveFamily = archiveInput.fixture.archiveFamily,
                    passId = passId,
                    itemIndex = index,
                    archivePath = archivePath,
                    isFolder = isFolder,
                    encrypted = archive.getProperty(index, PropID.ENCRYPTED) as? Boolean ?: false,
                    size = archive.getProperty(index, PropID.SIZE) as? Long,
                    packedSize = archive.getProperty(index, PropID.PACKED_SIZE) as? Long,
                    askMode = null,
                    outputRelativePath = null,
                    isArchiveCandidate = outputPolicy.isArchiveCandidate(archivePath),
                    renameApplied = false,
                    collisionIndex = null,
                    bytesWritten = 0,
                    operationResult = null,
                    pathRejected = false,
                )
            }

            archive.extract(indices.toIntArray(), false, callback)

            val finalizedRecords = callback.finalizeRecords(itemRecords)
            itemRecords.clear()
            itemRecords += finalizedRecords
            outputEntries += callback.outputEntries()

            val failingResults = itemRecords.filter { it.operationResult != null && it.operationResult != ExtractOperationResult.OK.name }
            if (failingResults.isNotEmpty()) {
                failure = FailureRecord(
                    fixtureId = archiveInput.fixture.id,
                    archiveFamily = archiveInput.fixture.archiveFamily,
                    archivePath = archiveInput.sourceFile.absolutePath,
                    errorType = "extract-operation-result",
                    message = "One or more extracted items returned a non-OK operation result.",
                    operationResults = failingResults.mapNotNull { it.operationResult },
                    stackTrace = null,
                    firstCause = null,
                    lastCause = null,
                    firstPotentialCause = null,
                    lastPotentialCause = null,
                )
            }
        } catch (exception: Throwable) {
            val sevenZipException = exception as? SevenZipException
            failure = FailureRecord(
                fixtureId = archiveInput.fixture.id,
                archiveFamily = archiveInput.fixture.archiveFamily,
                archivePath = archiveInput.sourceFile.absolutePath,
                errorType = exception::class.java.name,
                message = exception.message,
                operationResults = itemRecords.mapNotNull { it.operationResult },
                stackTrace = when (sevenZipException) {
                    null -> exception.renderStackTrace()
                    else -> sevenZipException.renderExtendedStackTrace()
                },
                firstCause = sevenZipException?.cause?.renderSummary(),
                lastCause = sevenZipException?.getCauseLastThrown()?.renderSummary(),
                firstPotentialCause = sevenZipException?.getCauseFirstPotentialThrown()?.renderSummary(),
                lastPotentialCause = sevenZipException?.getCauseLastPotentialThrown()?.renderSummary(),
            )
        } finally {
            callback?.let {
                val mergedRecords = it.finalizeRecords(itemRecords)
                itemRecords.clear()
                itemRecords += mergedRecords
                outputEntries.clear()
                outputEntries += it.outputEntries()
            }
            try {
                archive?.close()
            } catch (_: SevenZipException) {
            }
            try {
                randomAccessFile?.close()
            } catch (_: IOException) {
            }
            sevenZipVolumeCallback?.close()
            rarVolumeCallback?.close()
        }

        return ExtractionResult(
            itemRecords = itemRecords,
            outputEntries = outputEntries,
            volumeResolutions = volumeRecords,
            failure = failure,
        )
    }

    private fun ArchiveFamily.toArchiveFormat(): ArchiveFormat = when (this) {
        ArchiveFamily.ZIP -> ArchiveFormat.ZIP
        ArchiveFamily.RAR -> ArchiveFormat.RAR
        ArchiveFamily.SEVEN_ZIP -> ArchiveFormat.SEVEN_ZIP
    }

    private class ExtractionCallback(
        private val archive: IInArchive,
        private val archiveInput: PassArchiveInput,
        private val passId: String,
        private val runRoot: File,
        private val outputRoot: File,
        private val renameRule: RenameRule?,
        private val outputPolicy: Spike2OutputPolicy,
    ) : IArchiveExtractCallback {
        private val activeOutputs = ConcurrentHashMap<Int, ActiveOutput>()
        private val records = ConcurrentHashMap<Int, MutableArchiveItemRecord>()
        private val outputs = mutableListOf<OutputManifestEntry>()
        private val currentIndex = ThreadLocal<Int>()

        override fun getStream(index: Int, extractAskMode: ExtractAskMode): ISequentialOutStream? {
            currentIndex.set(index)
            val archivePath = archive.getProperty(index, PropID.PATH) as? String
            val isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
            val encrypted = archive.getProperty(index, PropID.ENCRYPTED) as? Boolean ?: false
            val size = archive.getProperty(index, PropID.SIZE) as? Long
            val packedSize = archive.getProperty(index, PropID.PACKED_SIZE) as? Long
            val archiveCandidate = outputPolicy.isArchiveCandidate(archivePath)
            val mutableRecord = records.getOrPut(index) {
                MutableArchiveItemRecord(
                    archivePath = archivePath,
                    isFolder = isFolder,
                    encrypted = encrypted,
                    size = size,
                    packedSize = packedSize,
                    isArchiveCandidate = archiveCandidate,
                )
            }
            mutableRecord.askMode = extractAskMode.name

            if (extractAskMode != ExtractAskMode.EXTRACT || isFolder) {
                return null
            }

            val target = outputPolicy.resolveTarget(
                outputRoot = outputRoot,
                archivePath = archivePath,
                renameRule = renameRule,
                isArchiveCandidate = archiveCandidate,
            )
            mutableRecord.outputRelativePath = target.outputRelativePath
            mutableRecord.renameApplied = target.renameApplied
            mutableRecord.collisionIndex = target.collisionIndex
            mutableRecord.pathRejected = target.pathRejected

            val tempFile = File(target.file.parentFile, ".${target.file.name}.part-$index")
            tempFile.parentFile?.mkdirs()
            val outputStream = FileOutputStream(tempFile)
            activeOutputs[index] = ActiveOutput(
                target = target,
                tempFile = tempFile,
                outputStream = outputStream,
            )
            return ISequentialOutStream { data ->
                outputStream.write(data)
                val active = activeOutputs[index]
                if (active != null) {
                    active.bytesWritten += data.size.toLong()
                }
                data.size
            }
        }

        override fun prepareOperation(extractAskMode: ExtractAskMode) = Unit

        override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
            val index = currentIndex.get() ?: return
            val active = activeOutputs.remove(index)
            val record = records.getOrPut(index) {
                MutableArchiveItemRecord(
                    archivePath = archive.getProperty(index, PropID.PATH) as? String,
                    isFolder = archive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false,
                    encrypted = archive.getProperty(index, PropID.ENCRYPTED) as? Boolean ?: false,
                    size = archive.getProperty(index, PropID.SIZE) as? Long,
                    packedSize = archive.getProperty(index, PropID.PACKED_SIZE) as? Long,
                    isArchiveCandidate = outputPolicy.isArchiveCandidate(archive.getProperty(index, PropID.PATH) as? String),
                )
            }
            record.operationResult = extractOperationResult.name

            if (active == null) {
                return
            }

            active.outputStream.flush()
            active.outputStream.close()
            record.bytesWritten = max(record.bytesWritten, active.bytesWritten)
            if (extractOperationResult == ExtractOperationResult.OK) {
                active.target.file.parentFile?.mkdirs()
                if (!active.tempFile.renameTo(active.target.file)) {
                    active.tempFile.copyTo(active.target.file, overwrite = true)
                    active.tempFile.delete()
                }
                outputs += OutputManifestEntry(
                    fixtureId = archiveInput.fixture.id,
                    passId = passId,
                    outputRelativePath = active.target.outputRelativePath,
                    itemIndex = index,
                    bytesWritten = active.bytesWritten,
                    isArchiveCandidate = record.isArchiveCandidate,
                    renameApplied = record.renameApplied,
                    collisionIndex = record.collisionIndex,
                )
            } else {
                active.tempFile.delete()
            }
        }

        override fun setCompleted(completeValue: Long) = Unit

        override fun setTotal(total: Long) = Unit

        fun finalizeRecords(baseRecords: List<ArchiveItemRecord>): List<ArchiveItemRecord> =
            baseRecords.map { base ->
                val recorded = records[base.itemIndex]
                if (recorded == null) {
                    base
                } else {
                    base.copy(
                        askMode = recorded.askMode,
                        outputRelativePath = recorded.outputRelativePath,
                        renameApplied = recorded.renameApplied,
                        collisionIndex = recorded.collisionIndex,
                        bytesWritten = recorded.bytesWritten,
                        operationResult = recorded.operationResult,
                        pathRejected = recorded.pathRejected,
                    )
                }
            }

        fun outputEntries(): List<OutputManifestEntry> = outputs.sortedBy { it.outputRelativePath }

        private data class ActiveOutput(
            val target: OutputTarget,
            val tempFile: File,
            val outputStream: FileOutputStream,
            var bytesWritten: Long = 0,
        )

        private data class MutableArchiveItemRecord(
            val archivePath: String?,
            val isFolder: Boolean,
            val encrypted: Boolean,
            val size: Long?,
            val packedSize: Long?,
            val isArchiveCandidate: Boolean,
            var askMode: String? = null,
            var outputRelativePath: String? = null,
            var renameApplied: Boolean = false,
            var collisionIndex: Int? = null,
            var bytesWritten: Long = 0,
            var operationResult: String? = null,
            var pathRejected: Boolean = false,
        )
    }
}
