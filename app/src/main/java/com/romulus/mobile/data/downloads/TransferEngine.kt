package com.romulus.mobile.data.downloads

sealed interface TransferResult {
    data object Success : TransferResult

    data class Failure(
        val reason: String,
        val retryable: Boolean,
        val partialExists: Boolean
    ) : TransferResult

    data object Paused : TransferResult

    data object Cancelled : TransferResult
}

enum class TransferDirective {
    CONTINUE,
    PAUSE,
    CANCEL
}

interface TransferEngine {
    suspend fun transfer(
        downloadUrl: String,
        outputUri: String,
        startByte: Long,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long?) -> Unit,
        resolveDirective: suspend () -> TransferDirective
    ): TransferResult
}
