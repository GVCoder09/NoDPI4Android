package com.gvcoder.nodpi.data

import android.content.Context
import com.gvcoder.nodpi.core.normalizeDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Blacklist stored in `files/blacklist.txt`, one domain per line */
class BlacklistRepository(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    private val _domains = MutableStateFlow(load())
    val domains: StateFlow<List<String>> = _domains.asStateFlow()

    /**
     * Grows on every change made by the user. Domains found by the auto mode do not bump it:
     * the running proxy already knows about them, so no restart is needed
     */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Adds domains separated by whitespace, commas or new lines; returns how many were new */
    fun add(input: String): Int = synchronized(lock) {
        val current = _domains.value
        val known = current.toHashSet()
        val added = parse(input).filter { known.add(it) }
        if (added.isNotEmpty()) {
            save(current + added)
        }
        added.size
    }

    /** Domain detected by the auto blacklist of the running proxy */
    fun addDetected(domain: String) = synchronized(lock) {
        val name = normalizeDomain(domain) ?: return@synchronized
        val current = _domains.value
        if (name !in current) {
            save(current + name, bumpRevision = false)
        }
    }

    fun remove(domain: String) = synchronized(lock) {
        save(_domains.value - domain)
    }

    fun insert(domain: String, index: Int) = synchronized(lock) {
        val current = _domains.value
        if (domain !in current) {
            save(current.toMutableList().apply { add(index.coerceIn(0, size), domain) })
        }
    }

    fun clear() = synchronized(lock) {
        save(emptyList())
    }

    fun resetToDefault() = synchronized(lock) {
        save(DEFAULT_BLACKLIST)
    }

    fun export(): String = _domains.value.joinToString("\n", postfix = "\n")

    private fun load(): List<String> {
        if (!file.exists()) {
            runCatching { file.writeText(DEFAULT_BLACKLIST.joinToString("\n", postfix = "\n")) }
            return DEFAULT_BLACKLIST
        }
        return runCatching { parse(file.readText()) }.getOrDefault(DEFAULT_BLACKLIST)
    }

    private fun save(domains: List<String>, bumpRevision: Boolean = true) {
        _domains.value = domains
        if (bumpRevision) {
            _revision.value++
        }
        runCatching { file.writeText(domains.joinToString("\n", postfix = if (domains.isEmpty()) "" else "\n")) }
    }

    private fun parse(text: String): List<String> =
        text.lineSequence()
            .map { it.substringBefore('#') }
            .flatMap { it.split(',', ';', ' ', '\t') }
            .mapNotNull(::normalizeDomain)
            .distinct()
            .toList()

    companion object {
        private const val FILE_NAME = "blacklist.txt"

        val DEFAULT_BLACKLIST = listOf(
            "youtube.com",
            "youtu.be",
            "yt.be",
            "googlevideo.com",
            "ytimg.com",
            "ggpht.com",
            "gvt1.com",
            "youtube-nocookie.com",
            "youtube-ui.l.google.com",
            "youtubeembeddedplayer.googleapis.com",
            "youtube.googleapis.com",
            "youtubei.googleapis.com",
            "jnn-pa.googleapis.com",
            "yt-video-upload.l.google.com",
            "wide-youtube.l.google.com",
            "yt3.googleusercontent.com",
            "youtubekids.com",
        )
    }
}
