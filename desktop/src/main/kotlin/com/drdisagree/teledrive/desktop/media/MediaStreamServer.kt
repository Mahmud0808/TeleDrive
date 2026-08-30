package com.drdisagree.teledrive.desktop.media

import com.drdisagree.teledrive.core.common.SafeLog
import com.drdisagree.teledrive.core.media.MediaByteSource
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serves the file being streamed to the system's media player over loopback.
 * Bytes come straight from Telegram through a [MediaByteSource], so playback
 * starts while the file is still arriving and nothing lands in Downloads.
 *
 * Players probe with several parallel connections, so one source is shared by
 * them all and reads are serialized: separate sources would fight over the one
 * ranged download TDLib runs per file. Reads run inside the stream's own scope,
 * so switching files cancels every read still waiting on the old download
 * instead of leaving connections blocked until their buffering deadline.
 */
class MediaStreamServer {

    private class ActiveStream(
        val source: MediaByteSource,
        val mimeType: String
    ) {
        val readLock = Mutex()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        var closed = false

        fun shutdown() {
            closed = true
            scope.cancel()
            runCatching { source.close() }
        }
    }

    private var server: HttpServer? = null
    private var active: ActiveStream? = null

    @Synchronized
    fun serve(fileName: String, fileMimeType: String, factory: () -> MediaByteSource): String {
        val running = server ?: HttpServer.create(InetSocketAddress(LOOPBACK, 0), 0).also {
            it.createContext(CONTEXT) { exchange -> handle(exchange) }
            it.executor = Executors.newCachedThreadPool { task ->
                Thread(task, "media-stream").apply { isDaemon = true }
            }
            it.start()
            server = it
        }
        active?.shutdown()
        active = ActiveStream(factory(), fileMimeType.ifBlank { FALLBACK_MIME })
        val encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
            .replace("+", "%20")
        val url = "http://$LOOPBACK:${running.address.port}$CONTEXT/$encodedName"
        SafeLog.d(TAG, "serving $fileName as $url")
        return url
    }

    @Synchronized
    fun stop() {
        active?.shutdown()
        active = null
        server?.stop(0)
        server = null
    }

    private fun handle(exchange: HttpExchange) {
        val stream = synchronized(this) { active }
        if (stream == null || stream.closed) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }
        runCatching { respond(exchange, stream) }
            .onFailure {
                SafeLog.d(TAG, "stream connection ended: ${it::class.simpleName} ${it.message}")
            }
        exchange.close()
    }

    private fun respond(exchange: HttpExchange, stream: ActiveStream) {
        val size = lockedRead(stream) { stream.source.size() }
        val range = parseRange(exchange.requestHeaders.getFirst("Range"), size)
        val start = range?.first ?: 0L
        val end = range?.second ?: (size - 1)
        val length = end - start + 1

        exchange.responseHeaders.add("Content-Type", stream.mimeType)
        exchange.responseHeaders.add("Accept-Ranges", "bytes")
        if (range != null) {
            exchange.responseHeaders.add("Content-Range", "bytes $start-$end/$size")
        }
        if (exchange.requestMethod.equals("HEAD", ignoreCase = true)) {
            exchange.sendResponseHeaders(if (range != null) 206 else 200, -1)
            return
        }
        exchange.sendResponseHeaders(if (range != null) 206 else 200, length)

        exchange.responseBody.use { output ->
            var position = start
            while (position <= end && !stream.closed) {
                val toRead = minOf(CHUNK.toLong(), end - position + 1).toInt()
                val data = lockedRead(stream) { stream.source.read(position, toRead) }
                if (data.isEmpty()) break
                output.write(data)
                position += data.size
            }
            output.flush()
        }
    }

    private fun <T> lockedRead(stream: ActiveStream, block: suspend () -> T): T =
        runBlocking {
            stream.scope.async {
                stream.readLock.withLock { block() }
            }.await()
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
