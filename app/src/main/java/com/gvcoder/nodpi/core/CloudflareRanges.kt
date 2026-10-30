package com.gvcoder.nodpi.core

import java.net.InetAddress

/**
 * Published Cloudflare networks (cloudflare.com/ips).
 *
 * TSPU treats Cloudflare connections differently from other hosts: a blocked SNI is dropped even
 * when the ClientHello is fragmented, and a connection whose SNI it could not read (disorder) is
 * frozen after ~16 KB. Fragmentation therefore cannot unblock anything there but breaks allowed
 * sites, so ClientHello to these addresses is sent unchanged
 */
object CloudflareRanges {

    private val networks = listOf(
        "173.245.48.0/20",
        "103.21.244.0/22",
        "103.22.200.0/22",
        "103.31.4.0/22",
        "141.101.64.0/18",
        "108.162.192.0/18",
        "190.93.240.0/20",
        "188.114.96.0/20",
        "197.234.240.0/22",
        "198.41.128.0/17",
        "162.158.0.0/15",
        "104.16.0.0/13",
        "104.24.0.0/14",
        "172.64.0.0/13",
        "131.0.72.0/22",
        "2400:cb00::/32",
        "2606:4700::/32",
        "2803:f800::/32",
        "2405:b500::/32",
        "2405:8100::/32",
        "2a06:98c0::/29",
        "2c0f:f248::/32",
    ).map(Cidr::parse)

    fun contains(address: InetAddress?): Boolean {
        val bytes = address?.address ?: return false
        return networks.any { it.matches(bytes) }
    }

    private class Cidr(private val network: ByteArray, private val prefix: Int) {

        fun matches(address: ByteArray): Boolean {
            if (address.size != network.size) {
                return false
            }
            val fullBytes = prefix / 8
            for (i in 0 until fullBytes) {
                if (address[i] != network[i]) return false
            }
            val restBits = prefix % 8
            if (restBits == 0) {
                return true
            }
            val mask = (0xFF shl (8 - restBits)) and 0xFF
            return (address[fullBytes].toInt() and mask) == (network[fullBytes].toInt() and mask)
        }

        companion object {
            // Literal addresses only: InetAddress.getByName does not touch DNS for them
            fun parse(value: String): Cidr {
                val (ip, prefix) = value.split('/')
                return Cidr(InetAddress.getByName(ip).address, prefix.toInt())
            }
        }
    }
}
