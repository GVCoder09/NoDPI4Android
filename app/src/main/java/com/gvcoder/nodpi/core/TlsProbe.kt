package com.gvcoder.nodpi.core

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** Detection of blocked domains used by [AutoBlacklist] */
object TlsProbe {

    private const val TIMEOUT_MS = 4_000

    /**
     * Opens a direct TLS connection to [domain]:443. The domain is considered blocked
     * when TCP connects but the TLS handshake times out: that is how DPI drops a ClientHello
     */
    fun isHandshakeTimingOut(domain: String): Boolean {
        val raw = Socket(Proxy.NO_PROXY)
        try {
            try {
                raw.connect(InetSocketAddress(domain, 443), TIMEOUT_MS)
            } catch (_: Exception) {
                return false
            }
            raw.soTimeout = TIMEOUT_MS

            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            val tls = factory.createSocket(raw, domain, 443, true) as SSLSocket
            return try {
                tls.use { it.startHandshake() }
                false
            } catch (_: SocketTimeoutException) {
                true
            } catch (_: Exception) {
                false
            }
        } finally {
            runCatching { raw.close() }
        }
    }
}
