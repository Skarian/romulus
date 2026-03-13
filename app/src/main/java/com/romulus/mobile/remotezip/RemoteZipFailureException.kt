package com.romulus.mobile.remotezip

internal class RemoteZipFailureException(
    val stage: String,
    val code: String,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
