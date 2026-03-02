package com.romulus.mobile.core.text

interface UserFacingErrorMapper {
    fun map(throwable: Throwable): String

    class Default : UserFacingErrorMapper {
        override fun map(throwable: Throwable): String {
            return throwable.message?.takeIf { it.isNotBlank() }
                ?: "Unexpected error. Please retry."
        }
    }
}
