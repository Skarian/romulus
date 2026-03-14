package com.romulus.spikes.spike5.config

data class Spike5Config(
    val apiToken: String,
    val magnet: String,
    val selectedFilePath: String,
) {
    companion object {
        fun from(values: Map<String, String>): Spike5Config {
            return Spike5Config(
                apiToken = requireNonBlank(values, "RD_API_TOKEN"),
                magnet = requireNonBlank(values, "SPIKE5_MAGNET"),
                selectedFilePath = normalizePath(requireNonBlank(values, "SPIKE5_SELECTED_FILE_PATH")),
            )
        }

        private fun requireNonBlank(values: Map<String, String>, key: String): String {
            val value = values[key]?.trim()
            require(!value.isNullOrEmpty()) { "Missing required Spike 5 value: $key" }
            return value
        }

        private fun normalizePath(raw: String): String {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty()) { "Spike 5 path values must not be blank" }
            return if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        }
    }
}
