package com.romulus.spikes.spike2

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream

class RecordingOpenCallback : IArchiveOpenCallback {
    override fun setTotal(files: Long?, bytes: Long?) = Unit

    override fun setCompleted(files: Long?, bytes: Long?) = Unit
}

class RecordingSevenZipVolumeCallback(
    private val fixtureId: String,
    private val firstVolume: File,
    private val records: MutableList<VolumeResolutionRecord>,
) : IArchiveOpenVolumeCallback {
    private val openedFiles = linkedMapOf<String, RandomAccessFile>()

    override fun getProperty(propID: PropID): Any? = when (propID) {
        PropID.NAME -> firstVolume.absolutePath
        PropID.SIZE -> firstVolume.length()
        PropID.IS_FOLDER -> false
        else -> null
    }

    override fun getStream(filename: String): IInStream? {
        val volumeFile = resolveVolumeFile(filename)
        val randomAccessFile = try {
            openedFiles.getOrPut(volumeFile.absolutePath) {
                RandomAccessFile(volumeFile, "r")
            }.apply {
                seek(0)
            }
        } catch (_: IOException) {
            null
        }
        records += VolumeResolutionRecord(
            fixtureId = fixtureId,
            requestedName = filename,
            resolved = randomAccessFile != null,
            resolvedDevicePath = randomAccessFile?.let { volumeFile.absolutePath },
            requestedAtUtc = nowUtc(),
        )
        return randomAccessFile?.let(::RandomAccessFileInStream)
    }

    fun close() {
        openedFiles.values.forEach {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        openedFiles.clear()
    }

    private fun resolveVolumeFile(filename: String): File {
        val candidate = File(filename)
        return if (candidate.isAbsolute) {
            candidate
        } else {
            requireNotNull(firstVolume.parentFile) { "First 7z multipart volume must have a parent directory." }
                .resolve(filename)
        }
    }
}

class RecordingRarVolumeCallback(
    private val fixtureId: String,
    private val firstVolume: File,
    private val records: MutableList<VolumeResolutionRecord>,
) : IArchiveOpenVolumeCallback, IArchiveOpenCallback {
    private val openedFiles = linkedMapOf<String, RandomAccessFile>()
    private var currentName: String = firstVolume.absolutePath

    override fun getProperty(propID: PropID): Any? = when (propID) {
        PropID.NAME -> currentName
        PropID.SIZE -> File(currentName).takeIf { it.exists() }?.length()
        PropID.IS_FOLDER -> false
        else -> null
    }

    override fun getStream(filename: String): IInStream? {
        val volumeFile = resolveVolumeFile(filename)
        val randomAccessFile = try {
            openedFiles.getOrPut(volumeFile.absolutePath) {
                RandomAccessFile(volumeFile, "r")
            }.apply {
                seek(0)
            }
        } catch (_: IOException) {
            null
        }
        if (randomAccessFile != null) {
            currentName = volumeFile.absolutePath
        }
        records += VolumeResolutionRecord(
            fixtureId = fixtureId,
            requestedName = filename,
            resolved = randomAccessFile != null,
            resolvedDevicePath = randomAccessFile?.let { volumeFile.absolutePath },
            requestedAtUtc = nowUtc(),
        )
        return randomAccessFile?.let(::RandomAccessFileInStream)
    }

    override fun setTotal(files: Long?, bytes: Long?) = Unit

    override fun setCompleted(files: Long?, bytes: Long?) = Unit

    fun close() {
        openedFiles.values.forEach {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        openedFiles.clear()
    }

    private fun resolveVolumeFile(filename: String): File {
        val candidate = File(filename)
        return if (candidate.isAbsolute) {
            candidate
        } else {
            requireNotNull(firstVolume.parentFile) { "First RAR multipart volume must have a parent directory." }
                .resolve(filename)
        }
    }
}
