package com.gvcoder.nodpi.core

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.net.Socket

/**
 * Disorder: the first segment is sent with TTL = 1, so it dies on the first hop before
 * reaching DPI, then the rest is sent normally. The kernel retransmits the first segment
 * later with the restored TTL, so DPI sees the stream out of order
 */
object Disorder {

    private const val LOW_TTL = 1
    private const val DEFAULT_TTL = -1
    private const val SETTLE_MS = 10L

    private val TTL_OPTIONS = listOf(
        OsConstants.IPPROTO_IP to OsConstants.IP_TTL,
        OsConstants.IPPROTO_IPV6 to OsConstants.IPV6_UNICAST_HOPS,
    )

    fun send(socket: Socket, first: ByteArray, rest: ByteArray) {
        val out = socket.getOutputStream()
        socket.tcpNoDelay = true

        val dup = runCatching { ParcelFileDescriptor.fromSocket(socket) }.getOrNull()
        if (dup == null) {
            out.write(first)
            out.write(rest)
            return
        }

        dup.use {
            val fd = it.fileDescriptor
            // Java sockets on Android are dual-stack, so both IPv4 TTL and IPv6 hop limit are set:
            // the kernel picks whichever applies to the actual connection
            TTL_OPTIONS.forEach { (level, option) -> setInt(fd, level, option, LOW_TTL) }
            try {
                out.write(first)
                Thread.sleep(SETTLE_MS)
            } finally {
                // getsockopt is not public on Android; -1 restores the system default TTL / hop limit
                TTL_OPTIONS.forEach { (level, option) -> setInt(fd, level, option, DEFAULT_TTL) }
            }
        }

        out.write(rest)
    }

    private fun setInt(fd: FileDescriptor, level: Int, option: Int, value: Int) {
        runCatching { Os.setsockoptInt(fd, level, option, value) }
    }
}
