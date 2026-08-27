package com.drdisagree.teledrive.core.proxy

import android.util.Base64
import com.drdisagree.teledrive.core.dispatchers.DispatcherProvider
import com.drdisagree.teledrive.core.telegram.TelegramProxy
import com.drdisagree.teledrive.core.telegram.TelegramProxyType
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks a proxy without TDLib, which refuses every request until it has been
 * given API credentials. SOCKS5 and HTTP speak in the clear, so both can be
 * driven all the way to a Telegram data center and answer the real question.
 * MTProto hides its handshake behind the secret, so only the connection itself
 * can be confirmed.
 */
@Singleton
class ProxyProbe @Inject constructor(
    private val dispatchers: DispatcherProvider
) {

    suspend fun probe(proxy: TelegramProxy): ProxyProbeResult = withContext(dispatchers.io) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(proxy.host, proxy.port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                when (proxy.type) {
                    TelegramProxyType.SOCKS5 -> socks5(socket, proxy)
                    TelegramProxyType.HTTP -> http(socket, proxy)
                    TelegramProxyType.MTPROTO -> mtproto(socket)
                }
            }
        }.getOrDefault(ProxyProbeResult.UNREACHABLE)
    }

    private fun socks5(socket: Socket, proxy: TelegramProxy): ProxyProbeResult {
        val output = socket.getOutputStream()
        val input = socket.getInputStream()
        val username = proxy.username.orEmpty()
        val password = proxy.password.orEmpty()
        val offersLogin = username.isNotEmpty() || password.isNotEmpty()

        output.writeAll(
            if (offersLogin) {
                byteArrayOf(SOCKS_VERSION, 2, AUTH_NONE, AUTH_LOGIN)
            } else {
                byteArrayOf(SOCKS_VERSION, 1, AUTH_NONE)
            }
        )
        val greeting = input.readExactly(2) ?: return ProxyProbeResult.REACHABLE
        if (greeting[0] != SOCKS_VERSION) return ProxyProbeResult.REACHABLE

        when (greeting[1]) {
            AUTH_NONE -> Unit
            AUTH_LOGIN -> {
                if (!offersLogin) return ProxyProbeResult.REACHABLE
                val login = ByteArrayOutputStream()
                login.write(1)
                username.toByteArray().let {
                    login.write(it.size)
                    login.write(it)
                }
                password.toByteArray().let {
                    login.write(it.size)
                    login.write(it)
                }
                output.writeAll(login.toByteArray())
                val accepted = input.readExactly(2) ?: return ProxyProbeResult.REACHABLE
                if (accepted[1] != STATUS_OK) return ProxyProbeResult.REACHABLE
            }

            else -> return ProxyProbeResult.REACHABLE
        }

        val request = ByteArrayOutputStream()
        request.write(byteArrayOf(SOCKS_VERSION, CMD_CONNECT, 0, ADDRESS_IPV4))
        request.write(InetAddress.getByName(DC_HOST).address)
        request.write((DC_PORT shr 8) and 0xFF)
        request.write(DC_PORT and 0xFF)
        output.writeAll(request.toByteArray())

        val reply = input.readExactly(4) ?: return ProxyProbeResult.REACHABLE
        return if (reply[1] == STATUS_OK) {
            ProxyProbeResult.ANSWERED
        } else {
            ProxyProbeResult.REACHABLE
        }
    }

    private fun http(socket: Socket, proxy: TelegramProxy): ProxyProbeResult {
        val target = "$DC_HOST:$DC_PORT"
        val request = StringBuilder()
            .append("CONNECT ").append(target).append(" HTTP/1.1\r\n")
            .append("Host: ").append(target).append("\r\n")
        val username = proxy.username.orEmpty()
        val password = proxy.password.orEmpty()
        if (username.isNotEmpty() || password.isNotEmpty()) {
            val credentials = Base64.encodeToString(
                "$username:$password".toByteArray(),
                Base64.NO_WRAP
            )
            request.append("Proxy-Authorization: Basic ").append(credentials).append("\r\n")
        }
        request.append("\r\n")
        socket.getOutputStream().writeAll(request.toString().toByteArray())

        val status = socket.getInputStream().readLine() ?: return ProxyProbeResult.REACHABLE
        return if (status.contains(" $HTTP_OK ") || status.endsWith(" $HTTP_OK")) {
            ProxyProbeResult.ANSWERED
        } else {
            ProxyProbeResult.REACHABLE
        }
    }

    /**
     * The real handshake is keyed with the secret, so a stand-in frame can only
     * show whether something is listening and willing to hold the connection. A
     * server that hangs up at once is reported as unreachable; silence is what a
     * working MTProto proxy does with a frame it cannot read.
     */
    private fun mtproto(socket: Socket): ProxyProbeResult {
        socket.getOutputStream().writeAll(Random.nextBytes(MTPROTO_FRAME))
        return try {
            if (socket.getInputStream().read() == -1) {
                ProxyProbeResult.UNREACHABLE
            } else {
                ProxyProbeResult.REACHABLE
            }
        } catch (_: SocketTimeoutException) {
            ProxyProbeResult.REACHABLE
        }
    }

    private fun OutputStream.writeAll(bytes: ByteArray) {
        write(bytes)
        flush()
    }

    private fun InputStream.readExactly(count: Int): ByteArray? {
        val buffer = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val read = read(buffer, filled, count - filled)
            if (read == -1) return null
            filled += read
        }
        return buffer
    }

    private fun InputStream.readLine(): String? {
        val line = ByteArrayOutputStream()
        while (line.size() < MAX_STATUS_LINE) {
            val byte = read()
            if (byte == -1) return line.takeIf { it.size() > 0 }?.toString(Charsets.ISO_8859_1.name())
            if (byte == '\n'.code) break
            if (byte != '\r'.code) line.write(byte)
        }
        return line.toString(Charsets.ISO_8859_1.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000
        const val DC_HOST = "149.154.167.51"
        const val DC_PORT = 443
        const val SOCKS_VERSION: Byte = 5
        const val AUTH_NONE: Byte = 0
        const val AUTH_LOGIN: Byte = 2
        const val CMD_CONNECT: Byte = 1
        const val ADDRESS_IPV4: Byte = 1
        const val STATUS_OK: Byte = 0
        const val HTTP_OK = 200
        const val MTPROTO_FRAME = 64
        const val MAX_STATUS_LINE = 256
    }
}
