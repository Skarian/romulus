package com.romulus.mobile.domain.files

class GlobIgnoreMatcher private constructor(
    private val regexes: List<Regex>
) {
    fun matches(candidate: String): Boolean {
        val basename = candidate.substringAfterLast('/').substringAfterLast('\\')
        return regexes.any { it.matches(basename) }
    }

    companion object {
        fun from(patterns: List<String>): Result<GlobIgnoreMatcher> {
            return runCatching {
                val regexes = patterns.map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map(::globToRegex)
                    .map { Regex(it, setOf(RegexOption.IGNORE_CASE)) }
                GlobIgnoreMatcher(regexes)
            }
        }

        private fun globToRegex(glob: String): String {
            val out = StringBuilder("^")
            var i = 0
            while (i < glob.length) {
                when (val c = glob[i]) {
                    '*' -> out.append(".*")
                    '?' -> out.append('.')
                    '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> {
                        out.append('\\').append(c)
                    }
                    else -> out.append(c)
                }
                i += 1
            }
            out.append('$')
            return out.toString()
        }
    }
}
