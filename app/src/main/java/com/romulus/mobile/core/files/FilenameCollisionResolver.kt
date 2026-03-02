package com.romulus.mobile.core.files

interface FilenameCollisionResolver {
    fun resolve(existingNames: Set<String>, candidateName: String): String

    class Default : FilenameCollisionResolver {
        override fun resolve(existingNames: Set<String>, candidateName: String): String {
            if (candidateName !in existingNames) return candidateName
            val dotIndex = candidateName.lastIndexOf('.')
            val hasExtension = dotIndex > 0 && dotIndex < candidateName.length - 1
            val base = if (hasExtension) candidateName.substring(0, dotIndex) else candidateName
            val extension = if (hasExtension) candidateName.substring(dotIndex) else ""
            var suffix = 1
            while (true) {
                val candidate = "$base ($suffix)$extension"
                if (candidate !in existingNames) return candidate
                suffix += 1
            }
        }
    }
}
