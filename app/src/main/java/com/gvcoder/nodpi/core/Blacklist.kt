package com.gvcoder.nodpi.core

import java.util.concurrent.ConcurrentHashMap

/** Decides whether the ClientHello of a domain must be fragmented */
interface Blacklist {
    fun isBlocked(domain: String): Boolean

    /** Called before connecting to [domain]; lets an implementation inspect it first */
    fun check(domain: String) = Unit
}

/** Fragments all domains without exception */
object AllDomainsBlacklist : Blacklist {
    override fun isBlocked(domain: String) = true
}

/** Blacklist with strict or loose domain matching */
class DomainBlacklist(
    domains: Iterable<String>,
    private val matching: DomainMatching,
) : Blacklist {

    private val blocked: Set<String> = domains.mapNotNullTo(HashSet()) { normalizeDomain(it) }

    val size: Int get() = blocked.size

    override fun isBlocked(domain: String): Boolean {
        val name = normalizeDomain(domain) ?: return false

        if (matching == DomainMatching.LOOSE && blocked.any { name.contains(it) }) {
            return true
        }

        if (name in blocked) {
            return true
        }

        var dot = name.indexOf('.')
        while (dot >= 0) {
            if (name.substring(dot + 1) in blocked) {
                return true
            }
            dot = name.indexOf('.', dot + 1)
        }

        return false
    }
}

/**
 * Blacklist that, besides the known domains, detects blocked ones on the fly:
 * a domain whose TLS handshake times out is considered blocked and reported via [onDetected]
 */
class AutoBlacklist(
    known: Iterable<String>,
    matching: DomainMatching,
    private val onDetected: (String) -> Unit,
    private val probe: (String) -> Boolean = TlsProbe::isHandshakeTimingOut,
) : Blacklist {

    private val known = DomainBlacklist(known, matching)
    private val detected = ConcurrentHashMap.newKeySet<String>()
    private val whitelist = ConcurrentHashMap.newKeySet<String>()

    override fun isBlocked(domain: String): Boolean {
        val name = normalizeDomain(domain) ?: return false
        return name in detected || known.isBlocked(name)
    }

    override fun check(domain: String) {
        val name = normalizeDomain(domain) ?: return
        if (name in whitelist || isBlocked(name)) {
            return
        }

        if (probe(name)) {
            if (detected.add(name)) {
                onDetected(name)
            }
        } else {
            whitelist.add(name)
        }
    }
}

/**
 * Brings user input or a blacklist line to a bare lowercase domain:
 * strips scheme, path, port, `*.`/`www.` prefixes. Returns null for comments and garbage
 */
fun normalizeDomain(raw: String): String? {
    var name = raw.trim().lowercase()
    if (name.length < 2 || name.startsWith("#")) {
        return null
    }

    name = name.substringAfter("://")
        .substringBefore('/')
        .substringBefore('?')
        .removePrefix("*.")
        .removePrefix("www.")

    if (!name.startsWith("[") && name.count { it == ':' } == 1) {
        name = name.substringBefore(':')
    }

    name = name.trim('.')

    if (name.length < 2 || name.any { it.isWhitespace() || it == '#' }) {
        return null
    }
    return name
}
