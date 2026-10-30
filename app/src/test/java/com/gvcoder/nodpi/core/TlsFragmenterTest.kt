package com.gvcoder.nodpi.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TlsFragmenterTest {

    private val host = "www.youtube.com"

    /** ClientHello-like body: some non-zero bytes, a server_name extension, more non-zero bytes */
    private fun clientHelloBody(name: String): ByteArray {
        val nameBytes = name.toByteArray()
        val listLen = nameBytes.size + 3
        val extLen = listLen + 2
        val sni = byteArrayOf(
            0x00, 0x00, // extension type: server_name
            0x00, extLen.toByte(),
            0x00, listLen.toByte(),
            0x00, // name type: host_name
            0x00, nameBytes.size.toByte(),
        ) + nameBytes
        val prefix = ByteArray(40) { (it % 200 + 1).toByte() }
        val suffix = ByteArray(25) { (it % 100 + 7).toByte() }
        return prefix + sni + suffix
    }

    private fun header(length: Int) = byteArrayOf(0x16, 0x03, 0x01, (length ushr 8).toByte(), length.toByte())

    @Test
    fun findsSni() {
        val body = clientHelloBody(host)
        val range = TlsFragmenter.findSni(body)!!
        assertEquals(host, String(body.copyOfRange(range.first, range.last + 1)))
    }

    @Test
    fun noSniReturnsOriginalRecord() {
        val body = ByteArray(64) { 0x11 }
        assertNull(TlsFragmenter.findSni(body))
        val parts = TlsFragmenter.fragment(header(body.size), body)
        assertEquals(1, parts.size)
        assertArrayEquals(header(body.size) + body, parts[0])
    }

    @Test
    fun fragmentsAroundSniInTwoByteChunks() {
        val body = clientHelloBody(host)
        val parts = TlsFragmenter.fragment(header(body.size), body)

        // before SNI + ceil(15 / 2) chunks of SNI + after SNI
        assertEquals(1 + 8 + 1, parts.size)

        val payload = parts.map { part ->
            assertEquals(0x16.toByte(), part[0])
            assertEquals(0x03.toByte(), part[1])
            assertEquals(0x04.toByte(), part[2])
            val length = ((part[3].toInt() and 0xFF) shl 8) or (part[4].toInt() and 0xFF)
            assertEquals(part.size - 5, length)
            part.copyOfRange(5, part.size)
        }
        assertArrayEquals(body, payload.join())
        payload.subList(1, payload.size - 1).dropLast(1).forEach { assertEquals(2, it.size) }
    }

    @Test
    fun strictMatchingCoversParentDomains() {
        val blacklist = DomainBlacklist(listOf("youtube.com", "www.googlevideo.com", "# comment"), DomainMatching.STRICT)
        assertTrue(blacklist.isBlocked("youtube.com"))
        assertTrue(blacklist.isBlocked("www.youtube.com"))
        assertTrue(blacklist.isBlocked("rr1---sn-abc.googlevideo.com"))
        assertFalse(blacklist.isBlocked("notyoutube.com"))
        assertFalse(blacklist.isBlocked("example.com"))
        assertEquals(2, blacklist.size)
    }

    @Test
    fun looseMatchingUsesSubstrings() {
        val blacklist = DomainBlacklist(listOf("youtube"), DomainMatching.LOOSE)
        assertTrue(blacklist.isBlocked("notyoutube.com"))
        assertFalse(DomainBlacklist(listOf("youtube"), DomainMatching.STRICT).isBlocked("notyoutube.com"))
    }

    @Test
    fun autoBlacklistRemembersDetectedDomains() {
        val detected = mutableListOf<String>()
        val blacklist = AutoBlacklist(
            known = listOf("youtube.com"),
            matching = DomainMatching.STRICT,
            onDetected = { detected += it },
            probe = { it == "blocked.example" },
        )
        blacklist.check("www.youtube.com")
        blacklist.check("blocked.example")
        blacklist.check("free.example")
        assertTrue(blacklist.isBlocked("blocked.example"))
        assertFalse(blacklist.isBlocked("free.example"))
        assertEquals(listOf("blocked.example"), detected)
    }

    @Test
    fun normalizesUserInput() {
        assertEquals("youtube.com", normalizeDomain(" https://www.YouTube.com/watch?v=1 "))
        assertEquals("example.org", normalizeDomain("*.example.org:443"))
        assertNull(normalizeDomain("# comment"))
        assertNull(normalizeDomain("a"))
    }

    @Test
    fun detectsCloudflareAddresses() {
        fun ip(value: String) = java.net.InetAddress.getByName(value)
        assertTrue(CloudflareRanges.contains(ip("104.18.0.79")))
        assertTrue(CloudflareRanges.contains(ip("162.159.140.220")))
        assertTrue(CloudflareRanges.contains(ip("172.67.1.1")))
        assertTrue(CloudflareRanges.contains(ip("2606:4700::6812:4f")))
        assertFalse(CloudflareRanges.contains(ip("57.144.244.1")))
        assertFalse(CloudflareRanges.contains(ip("142.251.152.119")))
        assertFalse(CloudflareRanges.contains(ip("104.32.0.1")))
        assertFalse(CloudflareRanges.contains(null))
    }

    @Test
    fun parsesHostAndPort() {
        assertEquals("example.com" to 443, HttpRequest.splitHostPort("example.com:443", 80))
        assertEquals("example.com" to 80, HttpRequest.splitHostPort("example.com", 80))
        assertEquals("::1" to 8443, HttpRequest.splitHostPort("[::1]:8443", 443))
    }
}
