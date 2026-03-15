package com.romulus.mobile.realdebrid

import kotlinx.coroutines.CancellationException

internal suspend fun <T> captureResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
    Result.failure(error)
}
