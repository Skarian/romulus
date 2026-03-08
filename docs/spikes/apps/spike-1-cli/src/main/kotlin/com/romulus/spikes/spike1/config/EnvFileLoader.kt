package com.romulus.spikes.spike1.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readLines

class EnvFileLoader {
    fun load(envFile: Path): Map<String, String> {
        require(Files.exists(envFile)) { "Env file not found: $envFile" }
        val values = linkedMapOf<String, String>()
        envFile.readLines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                return@forEachIndexed
            }

            val separator = line.indexOf('=')
            require(separator > 0) { "Invalid env line ${index + 1} in $envFile" }
            val key = line.substring(0, separator).trim()
            require(key.isNotBlank()) { "Invalid env key on line ${index + 1} in $envFile" }
            val rawValue = line.substring(separator + 1).trim()
            values[key] = parseValue(rawValue)
        }
        return values
    }

    private fun parseValue(rawValue: String): String {
        if (rawValue.length >= 2) {
            val quote = rawValue.first()
            if ((quote == '"' || quote == '\'') && rawValue.last() == quote) {
                return buildString {
                    var escaping = false
                    rawValue.substring(1, rawValue.length - 1).forEach { character ->
                        if (escaping) {
                            append(
                                when (character) {
                                    'n' -> '\n'
                                    'r' -> '\r'
                                    't' -> '\t'
                                    '\\' -> '\\'
                                    '"' -> '"'
                                    '\'' -> '\''
                                    else -> character
                                }
                            )
                            escaping = false
                        } else if (character == '\\') {
                            escaping = true
                        } else {
                            append(character)
                        }
                    }
                    if (escaping) {
                        append('\\')
                    }
                }
            }
        }
        return rawValue
    }
}
