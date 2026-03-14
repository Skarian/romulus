package com.romulus.mobile.diagnostics.events

import java.net.URI

internal class DiagnosticsRedactor {
    fun redact(event: DiagnosticEvent): DiagnosticEvent = event.copy(
        event = sanitizeText(event.event),
        outcome = sanitizeText(event.outcome),
        context = event.context.mapValues { (key, value) ->
            sanitizeValue(key, value)
        }
    )

    private fun sanitizeValue(key: String, value: String): String {
        val normalizedKey = key.lowercase()
        return when {
            normalizedKey.contains("token") -> REDACTED
            normalizedKey.contains("apikey") -> REDACTED
            normalizedKey.contains("api_key") -> REDACTED
            normalizedKey.contains("authorization") -> REDACTED
            normalizedKey.contains("auth_header") -> REDACTED
            normalizedKey.contains("secret") -> REDACTED
            normalizedKey.contains("password") -> REDACTED
            normalizedKey.contains("link") -> sanitizeUrlText(value)
            normalizedKey.contains("url") -> sanitizeUrlText(value)
            normalizedKey.contains("uri") -> sanitizeUrlText(value)
            else -> sanitizeText(value)
        }
    }

    private fun sanitizeText(value: String): String = sanitizeUrlText(
        value.replace(BEARER_REGEX, "Bearer $REDACTED")
    )

    private fun sanitizeUrlText(value: String): String = URL_REGEX.replace(value) { match ->
        val raw = match.value
        runCatching { URI(raw) }.getOrNull()?.let { uri ->
            when {
                uri.host == null -> "URL[$REDACTED]"
                uri.host == "api.real-debrid.com" -> "URL[host=${uri.host},path=${uri.path}]"
                else -> "URL[host=${uri.host}]"
            }
        } ?: "URL[$REDACTED]"
    }
}

private const val REDACTED = "[REDACTED]"
private val URL_REGEX = Regex("https?://[^\\s]+")
private val BEARER_REGEX = Regex("Bearer\\s+[A-Za-z0-9._\\-]+")
