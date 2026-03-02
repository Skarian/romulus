package com.romulus.mobile.core.validation

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data class Invalid(val message: String) : ValidationResult

    val isValid: Boolean
        get() = this is Valid
}
