package com.drdisagree.teledrive.desktop.media

import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.media.MediaByteSource
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

/**
 * Serves the file being streamed to the system's media player over loopback.
 * Bytes come straight from Telegram through a [MediaByteSource], so playback
 * starts while the file is still arriving and nothing lands in Downloads.
 * Only one file streams at a time; a new one replaces the previous.
 */
class MediaStreamServer {

    private var server: HttpServer? = null
    private var sourceFactory: (() -> MediaByteSource)? = null
    private var mimeType: String = FALLBACK_MIME

    @Synchronized
    fun serve(fileName: String, fileMimeType: String, factory: () -> MediaByteSource): String {
        val active = server ?: HttpServer.create(InetSocketAddress(LOOPBACK, 0), 0).also {
            it.createContext(CONTEXT) { exchange -> handle(exchange) }
            it.executor = Executors.newCachedThreadPool { task ->
                Thread(task, "media-stream").apply { isDaemon = true }
            }
            it.start()
            server = it
        }
        sourceFactory = factory
        mimeType = fileMimeType.ifBlank { FALLBACK_MIME }
        val encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
            .replace("+", "%20")
        return "http://$LOOPBACK:${active.address.port}$CONTEXT/$encodedName"
    }

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        sourceFactory = null
    }

    private fun handle(exchange: HttpExchange) {
        val factory = synchronized(this) { sourceFactory }
        if (factory == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        runCatching { factory().use { source -> respond(exchange, source) } }
            .onFailure { SafeLog.d(TAG, "stream ended: ${it.message}") }
        exchange.close()
    }

    private fun respond(exchange: HttpExchange, source: MediaByteSource) {
        val size = runBlocking { source.size() }
        val range = parseRange(exchange.requestHeaders.getFirst("Range"), size)
        val start = range?.first ?: 0L
        val end = range?.second ?: (size - 1)
        val length = end - start + 1

        val currentMime = synchronized(this) { mimeType }
        exchange.responseHeaders.add("Content-Type", currentMime)
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        if (range != null) {
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$size")
            exchange.sendResponseHeaders(206, length)
        } else {
            exchange.sendResponseHeaders(200, length)
        }
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) return

        exchange.responseBody.use { output ->
            var position = start
            while (position <= end) {
                val toRead = minOf(CHUNK.toLong(), end - position + 1).toInt()
                val data = runBlocking { source.read(position, toRead) }
                if (data.isEmpty()) break
                output.write(data)
                position += data.size
            }
            output.flush()
        }
    }

    private fun parseRange(header: String?, size: Long): Pair<Long, Long>? {
        val value = header?.trim()?.takeIf { it.startsWith(PREFIX) } ?: return null
        val spec = value.removePrefix(PREFIX).split(",").first().trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startText = spec.take(dash)
        val endText = spec.substring(dash + 1)
        return when {
            startText.isEmpty() -> {
                val suffix = endText.toLongOrNull() ?: return null
                (size - suffix).coerceAtLeast(0) to size - 1
            }

            else -> {
                val start = startText.toLongOrNull() ?: return null
                val end = endText.toLongOrNull()?.coerceAtMost(size - 1) ?: (size - 1)
                if (start > end) return null
                start to end
            }
        }
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val CONTEXT = "/stream"
        const val PREFIX = "bytes="
        const val CHUNK = 256 * 1024
        const val FALLBACK_MIME = "application/octet-stream"
        const val TAG = "MediaStreamServer"
    }
}
