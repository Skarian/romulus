@file:Suppress("ClassSignature")

package com.romulus.mobile.realdebrid

internal class AuthRequiredException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
