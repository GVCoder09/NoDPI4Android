package com.gvcoder.nodpi.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class LogKind {
    /** TLS connection passed as is: domain is not in the blacklist */
    DIRECT,

    /** TLS connection whose ClientHello was fragmented */
    UNBLOCKED,

    /** Plain HTTP request or a non-TLS tunnel */
    PLAIN,

    ERROR,
}

data class LogEntry(
    val id: Long,
    val time: Long,
    val kind: LogKind,
    val host: String,
    val method: String,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val message: String? = null,
    /** Blocked domain on Cloudflare sent without fragmentation, see [CloudflareRanges] */
    val cloudflare: Boolean = false,
)

/** In-memory access and error log (replaces the desktop --log-access/--log-error files) */
class ProxyLog(private val capacity: Int = 500) {

    private val ids = AtomicLong()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Entries from the oldest to the newest */
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun access(kind: LogKind, host: String, method: String, bytesIn: Long, bytesOut: Long, cloudflare: Boolean = false) {
        append(
            LogEntry(ids.incrementAndGet(), System.currentTimeMillis(), kind, host, method, bytesIn, bytesOut, cloudflare = cloudflare),
        )
    }

    fun error(host: String, method: String, message: String) {
        append(LogEntry(ids.incrementAndGet(), System.currentTimeMillis(), LogKind.ERROR, host, method, message = message))
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private fun append(entry: LogEntry) {
        _entries.update { current ->
            if (current.size >= capacity) current.drop(current.size - capacity + 1) + entry else current + entry
        }
    }
}
