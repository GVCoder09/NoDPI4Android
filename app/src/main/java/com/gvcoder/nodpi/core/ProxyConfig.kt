package com.gvcoder.nodpi.core

/** Which domains get their ClientHello fragmented */
enum class BlacklistMode {
    /** Only domains from the blacklist */
    STANDARD,

    /** Every domain, the blacklist is ignored */
    ALL,

    /** Blacklist plus domains detected automatically by a TLS handshake timeout */
    AUTO,
}

/** How a requested domain is compared with blacklist entries */
enum class DomainMatching {
    /** Exact domain or any of its parent domains */
    STRICT,

    /** Additionally, any domain that contains a blacklist entry as a substring */
    LOOSE,
}

/** Configuration of a single proxy server run */
data class ProxyConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val blacklistMode: BlacklistMode = BlacklistMode.STANDARD,
    val domainMatching: DomainMatching = DomainMatching.STRICT,
    val disorder: Boolean = true,
    val username: String? = null,
    val password: String? = null,
) {
    val authEnabled: Boolean get() = username != null && password != null

    companion object {
        const val DEFAULT_HOST = "127.0.0.1"
        const val DEFAULT_PORT = 8881
    }
}
