package com.gvcoder.nodpi.core

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.SequenceInputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * HTTP(S) proxy server: proxy -> blacklist -> fragmentation with disorder.
 *
 * For `CONNECT` the first TLS record from the client is read, and if the domain is in the
 * [blacklist] its ClientHello is fragmented around SNI and sent with disorder.
 * After that the data is piped in both directions unchanged
 */
class ProxyServer(
    private val config: ProxyConfig,
    private val blacklist: Blacklist,
    private val stats: ProxyStats,
    private val log: ProxyLog,
) {

    private val executor: ExecutorService = Executors.newCachedThreadPool { task ->
        Thread(task, "nodpi-proxy").apply { isDaemon = true }
    }
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    /** Binds the listening socket; throws [IOException] if the address is unavailable */
    fun start() {
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(InetAddress.getByName(config.host), config.port), BACKLOG)
        } catch (e: IOException) {
            server.close()
            executor.shutdownNow()
            throw e
        }
        serverSocket = server
        running = true
        executor.execute { acceptLoop(server) }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
        executor.shutdownNow()
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running && !server.isClosed) {
            val client = try {
                server.accept()
            } catch (_: IOException) {
                continue
            }
            sockets += client
            try {
                executor.execute { handleConnection(client) }
            } catch (_: RejectedExecutionException) {
                close(client)
            }
        }
    }

    private fun handleConnection(client: Socket) {
        var remote: Socket? = null
        var info: ConnectionInfo? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = REQUEST_TIMEOUT_MS
            val clientIn = client.getInputStream()

            val head = readRequestHead(clientIn)
            if (head == null) {
                close(client)
                return
            }
            val request = HttpRequest.parse(head.bytes, head.headerEnd)
            info = ConnectionInfo(request.host, request.method)
            stats.addOutgoing(head.bytes.size)
            info.trafficOut.addAndGet(head.bytes.size.toLong())

            if (!isAuthorized(request)) {
                client.getOutputStream().write(PROXY_AUTH_REQUIRED)
                close(client)
                return
            }

            if (request.isConnect) {
                blacklist.check(request.host)
                remote = connectRemote(request.host, request.port)

                client.getOutputStream().write(CONNECTION_ESTABLISHED)
                stats.addIncoming(CONNECTION_ESTABLISHED.size)
                info.trafficIn.addAndGet(CONNECTION_ESTABLISHED.size.toLong())

                // Bytes the client sent right after CONNECT belong to the tunnel
                val leftover = head.bytes.copyOfRange(head.headerEnd, head.bytes.size)
                val tunnelIn = if (leftover.isEmpty()) clientIn else SequenceInputStream(ByteArrayInputStream(leftover), clientIn)

                client.soTimeout = 0
                if (!sendInitialTlsData(tunnelIn, remote, info)) {
                    close(remote)
                    close(client)
                    return
                }
                pipeBothWays(client, tunnelIn, remote, info)
            } else {
                remote = connectRemote(request.host, request.port)
                remote.getOutputStream().write(head.bytes)
                stats.countDirect()
                info.kind = LogKind.PLAIN

                client.soTimeout = 0
                pipeBothWays(client, clientIn, remote, info)
            }
        } catch (e: Exception) {
            handleConnectionError(client, remote, info, e)
        }
    }

    /** Reads the first TLS record and sends it, fragmented if the domain is blocked. False if the client left */
    private fun sendInitialTlsData(input: InputStream, remote: Socket, info: ConnectionInfo): Boolean {
        val first = input.read()
        if (first < 0) {
            return false
        }

        val header = ByteArray(TlsFragmenter.RECORD_HEADER_SIZE)
        header[0] = first.toByte()
        readFully(input, header, 1, header.size - 1)
        val out = remote.getOutputStream()

        if (header[0] != TlsFragmenter.HANDSHAKE_CONTENT_TYPE) {
            out.write(header)
            stats.addOutgoing(header.size)
            info.trafficOut.addAndGet(header.size.toLong())
            info.kind = LogKind.PLAIN
            return true
        }

        val length = ((header[3].toInt() and 0xFF) shl 8) or (header[4].toInt() and 0xFF)
        val body = ByteArray(length)
        readFully(input, body, 0, length)

        val blocked = blacklist.isBlocked(info.host)
        val cloudflare = blocked && CloudflareRanges.contains(remote.inetAddress)
        if (!blocked || cloudflare) {
            stats.countDirect()
            info.kind = LogKind.DIRECT
            info.cloudflare = cloudflare
            val record = header + body
            out.write(record)
            stats.addOutgoing(record.size)
            info.trafficOut.addAndGet(record.size.toLong())
            return true
        }

        stats.countUnblocked()
        info.kind = LogKind.UNBLOCKED

        val parts = TlsFragmenter.fragment(header, body)
        if (config.disorder && parts.size > 2) {
            Disorder.send(remote, parts[0] + parts[1], parts.subList(2, parts.size).join())
        } else {
            out.write(parts.join())
        }

        val sent = parts.sumOf { it.size }
        stats.addOutgoing(sent)
        info.trafficOut.addAndGet(sent.toLong())
        return true
    }

    private fun pipeBothWays(client: Socket, clientIn: InputStream, remote: Socket, info: ConnectionInfo) {
        try {
            executor.execute { pipe(remote.getInputStream(), remote, client, incoming = true, info) }
        } catch (e: RejectedExecutionException) {
            close(remote)
            close(client)
            return
        }
        pipe(clientIn, client, remote, incoming = false, info)
    }

    private fun pipe(input: InputStream, source: Socket, target: Socket, incoming: Boolean, info: ConnectionInfo) {
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            val out = target.getOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (incoming) {
                    stats.addIncoming(read)
                    info.trafficIn.addAndGet(read.toLong())
                } else {
                    stats.addOutgoing(read)
                    info.trafficOut.addAndGet(read.toLong())
                }
            }
        } catch (_: IOException) {
            // Peer closed or reset the connection: the tunnel is over
        } finally {
            // Closing the target also breaks the opposite pipe, which reads from it
            close(target)
            if (info.pipes.decrementAndGet() == 0) {
                close(source)
                log.access(info.kind, info.host, info.method, info.trafficIn.get(), info.trafficOut.get(), info.cloudflare)
            }
        }
    }

    private fun handleConnectionError(client: Socket, remote: Socket?, info: ConnectionInfo?, error: Exception) {
        if (running) {
            try {
                client.getOutputStream().write(INTERNAL_ERROR)
                stats.addIncoming(INTERNAL_ERROR.size)
            } catch (_: IOException) {
            }
            stats.countError()
            log.error(info?.host ?: "?", info?.method ?: "?", describe(error))
        }
        remote?.let(::close)
        close(client)
    }

    private fun connectRemote(host: String, port: Int): Socket {
        val socket = Socket(Proxy.NO_PROXY)
        sockets += socket
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        } catch (e: IOException) {
            close(socket)
            throw e
        }
        return socket
    }

    private fun isAuthorized(request: HttpRequest): Boolean {
        if (!config.authEnabled) {
            return true
        }
        val value = request.header("proxy-authorization") ?: return false
        val parts = value.trim().split(' ', limit = 2)
        if (parts.size != 2 || !parts[0].equals("basic", ignoreCase = true)) {
            return false
        }
        val decoded = try {
            String(Base64.decode(parts[1].trim(), Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val separator = decoded.indexOf(':')
        if (separator < 0) {
            return false
        }
        return decoded.substring(0, separator) == config.username && decoded.substring(separator + 1) == config.password
    }

    private fun close(socket: Socket) {
        sockets -= socket
        runCatching { socket.close() }
    }

    private class ConnectionInfo(val host: String, val method: String) {
        @Volatile
        var kind: LogKind = LogKind.PLAIN

        /** Blocked domain on Cloudflare: its ClientHello was deliberately left unfragmented */
        @Volatile
        var cloudflare = false
        val trafficIn = AtomicLong()
        val trafficOut = AtomicLong()
        val pipes = AtomicInteger(2)
    }

    private class RequestHead(val bytes: ByteArray, val headerEnd: Int)

    companion object {
        private const val BACKLOG = 128
        private const val BUFFER_SIZE = 16 * 1024
        private const val MAX_HEAD_SIZE = 64 * 1024
        private const val REQUEST_TIMEOUT_MS = 60_000
        private const val CONNECT_TIMEOUT_MS = 15_000

        private val CONNECTION_ESTABLISHED = "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray()
        private val INTERNAL_ERROR = "HTTP/1.1 500 Internal Server Error\r\n\r\n".toByteArray()
        private val PROXY_AUTH_REQUIRED = (
            "HTTP/1.1 407 Proxy Authentication Required\r\n" +
                "Proxy-Authenticate: Basic realm=\"NoDPI Proxy\"\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray()
        private val HEADER_END = "\r\n\r\n".toByteArray()

        /** Reads until the end of HTTP headers; everything read is kept, including bytes after them */
        private fun readRequestHead(input: InputStream): RequestHead? {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(4096)
            while (buffer.size() < MAX_HEAD_SIZE) {
                val read = input.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
                val bytes = buffer.toByteArray()
                val end = indexOf(bytes, HEADER_END)
                if (end >= 0) {
                    return RequestHead(bytes, end + HEADER_END.size)
                }
            }
            val bytes = buffer.toByteArray()
            return if (bytes.isEmpty()) null else RequestHead(bytes, bytes.size)
        }

        private fun readFully(input: InputStream, target: ByteArray, offset: Int, length: Int) {
            var done = 0
            while (done < length) {
                val read = input.read(target, offset + done, length - done)
                if (read < 0) throw EOFException("Connection closed after $done of $length bytes")
                done += read
            }
        }

        private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
            outer@ for (i in 0..data.size - pattern.size) {
                for (j in pattern.indices) {
                    if (data[i + j] != pattern[j]) continue@outer
                }
                return i
            }
            return -1
        }

        private fun describe(error: Throwable): String =
            listOfNotNull(error.javaClass.simpleName, error.message).joinToString(": ")
    }
}

/** First line and headers of a proxy request */
internal class HttpRequest(
    val method: String,
    val host: String,
    val port: Int,
    private val headers: List<String>,
) {
    val isConnect: Boolean get() = method == "CONNECT"

    fun header(name: String): String? {
        val prefix = "$name:"
        return headers.firstOrNull { it.startsWith(prefix, ignoreCase = true) }?.substring(prefix.length)?.trim()
    }

    companion object {
        fun parse(bytes: ByteArray, headerEnd: Int): HttpRequest {
            val lines = String(bytes, 0, headerEnd, Charsets.ISO_8859_1).split("\r\n")
            val first = lines[0].split(' ')
            require(first.size >= 2) { "Malformed request line" }
            val method = first[0]
            val headers = lines.drop(1).filter { it.isNotEmpty() }

            if (method == "CONNECT") {
                val (host, port) = splitHostPort(first[1], 443)
                return HttpRequest(method, host, port, headers)
            }

            val hostHeader = headers.firstOrNull { it.startsWith("host:", ignoreCase = true) }
                ?.substring(5)?.trim()
                ?: throw IllegalArgumentException("Missing Host header")
            val (host, port) = splitHostPort(hostHeader, 80)
            return HttpRequest(method, host, port, headers)
        }

        /** Splits `host:port` and `[ipv6]:port` */
        fun splitHostPort(value: String, defaultPort: Int): Pair<String, Int> {
            if (value.startsWith("[")) {
                val close = value.indexOf(']')
                require(close > 0) { "Malformed host: $value" }
                val host = value.substring(1, close)
                val port = value.substring(close + 1).removePrefix(":").toIntOrNull() ?: defaultPort
                return host to port
            }
            val colon = value.lastIndexOf(':')
            if (colon < 0 || value.indexOf(':') != colon) {
                return value to defaultPort
            }
            return value.substring(0, colon) to (value.substring(colon + 1).toIntOrNull() ?: defaultPort)
        }
    }
}
