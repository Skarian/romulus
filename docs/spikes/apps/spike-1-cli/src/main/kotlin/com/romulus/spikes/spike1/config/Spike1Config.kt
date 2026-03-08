package com.romulus.spikes.spike1.config

data class Spike1Config(
    val apiToken: String,
    val magnet: String,
    val rootSelectedPaths: List<String>,
    val directoryScope: String,
    val directorySelectedPaths: List<String>,
    val exactZipPath: String,
    val uncachedMagnet: String? = null,
    val uncachedSelectedPath: String? = null,
) {
    data class KnownUncachedInput(
        val magnet: String,
        val selectedPath: String,
    )

    companion object {
        fun from(values: Map<String, String>): Spike1Config {
            val uncachedMagnet = optionalNonBlank(values, "SPIKE1_UNCACHED_MAGNET")
            val uncachedSelectedPath = optionalPath(values, "SPIKE1_UNCACHED_SELECTED_PATH")
            require((uncachedMagnet == null) == (uncachedSelectedPath == null)) {
                "Spike 1 known-uncached inputs require both SPIKE1_UNCACHED_MAGNET and SPIKE1_UNCACHED_SELECTED_PATH"
            }
            return Spike1Config(
                apiToken = requireNonBlank(values, "RD_API_TOKEN"),
                magnet = requireNonBlank(values, "SPIKE1_MAGNET"),
                rootSelectedPaths = parsePathList(values, "SPIKE1_ROOT_SELECTED_PATHS"),
                directoryScope = normalizePath(requireNonBlank(values, "SPIKE1_DIRECTORY_SCOPE")),
                directorySelectedPaths = parsePathList(values, "SPIKE1_DIRECTORY_SELECTED_PATHS"),
                exactZipPath = normalizePath(requireNonBlank(values, "SPIKE1_EXACT_ZIP_PATH")),
                uncachedMagnet = uncachedMagnet,
                uncachedSelectedPath = uncachedSelectedPath,
            )
        }

        private fun requireNonBlank(values: Map<String, String>, key: String): String {
            val value = values[key]?.trim()
            require(!value.isNullOrEmpty()) { "Missing required Spike 1 value: $key" }
            return value
        }

        private fun parsePathList(values: Map<String, String>, key: String): List<String> {
            return requireNonBlank(values, key)
                .split(',')
                .map { normalizePath(it) }
                .distinct()
                .also { require(it.isNotEmpty()) { "Missing required Spike 1 value: $key" } }
        }

        private fun optionalNonBlank(values: Map<String, String>, key: String): String? {
            return values[key]
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        private fun optionalPath(values: Map<String, String>, key: String): String? {
            return optionalNonBlank(values, key)?.let(::normalizePath)
        }

        private fun normalizePath(raw: String): String {
            val trimmed = raw.trim()
            require(trimmed.isNotEmpty()) { "Spike 1 path values must not be blank" }
            return if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        }
    }

    fun requireKnownUncached(): KnownUncachedInput {
        return KnownUncachedInput(
            magnet = requireNotNull(uncachedMagnet) { "Missing required Spike 1 value: SPIKE1_UNCACHED_MAGNET" },
            selectedPath = requireNotNull(uncachedSelectedPath) { "Missing required Spike 1 value: SPIKE1_UNCACHED_SELECTED_PATH" },
        )
    }
}
