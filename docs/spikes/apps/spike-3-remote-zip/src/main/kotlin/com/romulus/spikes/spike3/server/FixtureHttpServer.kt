package com.romulus.spikes.spike3.server

import com.romulus.spikes.spike3.model.RangeMode
import com.sun.net.httpserver.Headers
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.math.max
import kotlin.math.min

class FixtureHttpServer(
    private val httpRoot: Path,
    private val port: Int,
) : AutoCloseable {
    private var server: HttpServer? = null

    fun start(): URI {
        check(server == null) { "Server already started" }
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        httpServer.createContext("/health") { exchange ->
            exchange.use {
                if (exchange.requestMethod != "GET") {
                    sendPlain(exchange, 405, "Method Not Allowed")
                    return@use
                }
                sendPlain(exchange, 200, "OK")
            }
        }
        RangeMode.entries.forEach { mode ->
            httpServer.createContext("/${mode.pathSegment}") { exchange ->
                exchange.use {
                    handleArchiveRequest(exchange, mode)
                }
            }
        }
        httpServer.start()
        server = httpServer
        val address = httpServer.address
        return URI("http://127.0.0.1:${address.port}/")
    }

    override fun close() {
        server?.stop(0)
        server = null
    }

    private fun handleArchiveRequest(exchange: HttpExchange, rangeMode: RangeMode) {
        val method = exchange.requestMethod
        if (method != "GET" && method != "HEAD") {
            sendPlain(exchange, 405, "Method Not Allowed")
            return
        }

        val requestPath = exchange.requestURI.path.removePrefix("/${rangeMode.pathSegment}/")
        val file = resolveArchive(requestPath)
        if (file == null || !Files.isRegularFile(file)) {
            sendPlain(exchange, 404, "Not Found")
            return
        }

        val fileSize = Files.size(file)
        val headers = exchange.responseHeaders
        headers.add("X-Spike-Range-Mode", rangeMode.traceLabel)
        headers.add("Content-Type", "application/zip")

        when (rangeMode) {
            RangeMode.FULL -> serveFullRange(exchange, file, fileSize, method)
            RangeMode.NONE -> serveNoRange(exchange, file, fileSize, method)
            RangeMode.CAPPED -> serveCappedRange(exchange, file, fileSize, method)
        }
    }

    private fun serveFullRange(exchange: HttpExchange, file: Path, fileSize: Long, method: String) {
        val headers = exchange.responseHeaders
        headers.add("Accept-Ranges", "bytes")
        val requestedRange = exchange.requestHeaders.getFirst("Range")
        if (requestedRange == null) {
            headers.add("Content-Length", fileSize.toString())
            if (method == "HEAD") {
                exchange.sendResponseHeaders(200, -1)
                return
            }
            exchange.sendResponseHeaders(200, fileSize)
            file.inputStream().use { input ->
                input.copyTo(exchange.responseBody)
            }
            return
        }

        val range = parseRange(requestedRange, fileSize, clampToEnd = true) ?: run {
            headers.add("Content-Range", "bytes */$fileSize")
            exchange.sendResponseHeaders(416, -1)
            return
        }
        writePartialContent(exchange, file, range.first, range.second, method)
    }

    private fun serveNoRange(exchange: HttpExchange, file: Path, fileSize: Long, method: String) {
        val headers = exchange.responseHeaders
        headers.add("Content-Length", fileSize.toString())
        if (method == "HEAD") {
            exchange.sendResponseHeaders(200, -1)
            return
        }
        exchange.sendResponseHeaders(200, fileSize)
        file.inputStream().use { input ->
            input.copyTo(exchange.responseBody)
        }
    }

    private fun serveCappedRange(exchange: HttpExchange, file: Path, fileSize: Long, method: String) {
        val headers = exchange.responseHeaders
        headers.add("Accept-Ranges", "bytes")
        val requestedRange = exchange.requestHeaders.getFirst("Range")
        if (requestedRange == null) {
            headers.add("Content-Length", fileSize.toString())
            if (method == "HEAD") {
                exchange.sendResponseHeaders(200, -1)
                return
            }
            exchange.sendResponseHeaders(200, fileSize)
            file.inputStream().use { input ->
                input.copyTo(exchange.responseBody)
            }
            return
        }

        val rawRange = parseRange(requestedRange, fileSize, clampToEnd = false) ?: run {
            headers.add("Content-Range", "bytes */$fileSize")
            exchange.sendResponseHeaders(416, -1)
            return
        }
        val requestedWidth = rawRange.second - rawRange.first + 1
        if (requestedWidth > MAX_CAPPED_WINDOW_BYTES) {
            headers.add("Content-Range", "bytes */$fileSize")
            exchange.sendResponseHeaders(416, -1)
            return
        }
        val normalizedEnd = min(rawRange.second, fileSize - 1)
        writePartialContent(exchange, file, rawRange.first, normalizedEnd, method)
    }

    private fun writePartialContent(exchange: HttpExchange, file: Path, start: Long, endInclusive: Long, method: String) {
        val responseLength = endInclusive - start + 1
        val headers = exchange.responseHeaders
        headers.add("Content-Length", responseLength.toString())
        headers.add("Content-Range", "bytes $start-$endInclusive/${Files.size(file)}")
        if (method == "HEAD") {
            exchange.sendResponseHeaders(206, -1)
            return
        }
        exchange.sendResponseHeaders(206, responseLength)
        file.inputStream().use { input ->
            input.skipNBytes(start)
            input.copyNBytesTo(exchange.responseBody, responseLength)
        }
    }

    private fun sendPlain(exchange: HttpExchange, statusCode: Int, body: String) {
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun resolveArchive(requestPath: String): Path? {
        val normalized = requestPath.trimStart('/').takeIf { it.isNotBlank() } ?: return null
        val candidate = httpRoot.resolve(normalized).normalize()
        return if (candidate.startsWith(httpRoot)) candidate else null
    }

    private fun parseRange(header: String, fileSize: Long, clampToEnd: Boolean): Pair<Long, Long>? {
        if (!header.startsWith("bytes=")) {
            return null
        }
        val parts = header.removePrefix("bytes=").split('-', limit = 2)
        if (parts.size != 2 || parts[0].isBlank()) {
            return null
        }
        val start = parts[0].toLongOrNull() ?: return null
        if (start >= fileSize) {
            return null
        }
        val requestedEnd = parts[1].toLongOrNull() ?: (fileSize - 1)
        val endInclusive = if (clampToEnd) min(requestedEnd, fileSize - 1) else requestedEnd
        if (endInclusive < start) {
            return null
        }
        return max(0, start) to endInclusive
    }

    private fun java.io.InputStream.copyNBytesTo(output: java.io.OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val bytesRead = read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
            if (bytesRead == -1) {
                break
            }
            output.write(buffer, 0, bytesRead)
            remaining -= bytesRead
        }
    }

    private fun Headers.add(name: String, value: String) {
        this.set(name, value)
    }

    private companion object {
        const val MAX_CAPPED_WINDOW_BYTES = 4096L
    }
}
