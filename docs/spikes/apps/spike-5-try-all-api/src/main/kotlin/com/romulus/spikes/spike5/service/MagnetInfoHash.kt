package com.romulus.spikes.spike5.service

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.min

object MagnetInfoHash {
    private val btihPattern = Regex("(?:^|[?&])xt=urn:btih:([^&]+)", RegexOption.IGNORE_CASE)
    private const val base32Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun extract(magnet: String): String? {
        val match = btihPattern.find(magnet) ?: return null
        val raw = URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8).trim()
        return when {
            raw.matches(Regex("[0-9a-fA-F]{40}")) -> raw.lowercase()
            raw.matches(Regex("[A-Z2-7a-z2-7]{32}")) -> decodeBase32(raw)
            else -> null
        }
    }

    private fun decodeBase32(value: String): String {
        val normalized = value.uppercase()
        var bits = 0
        var bitBuffer = 0
        val bytes = ArrayList<Byte>((normalized.length * 5) / 8)
        for (character in normalized) {
            val index = base32Alphabet.indexOf(character)
            require(index >= 0) { "Invalid base32 info hash" }
            bitBuffer = (bitBuffer shl 5) or index
            bits += 5
            while (bits >= 8) {
                bits -= 8
                bytes += ((bitBuffer shr bits) and 0xFF).toByte()
            }
        }
        return bytes.joinToString(separator = "") { byte ->
            val unsigned = byte.toInt() and 0xFF
            unsigned.toString(16).padStart(2, '0')
        }.substring(0, min(bytes.size * 2, 40))
    }
}
