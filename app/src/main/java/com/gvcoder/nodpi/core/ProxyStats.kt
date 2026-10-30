package com.gvcoder.nodpi.core

import java.util.concurrent.atomic.AtomicLong

/** Snapshot of the proxy statistics, speeds are in bits per second */
data class StatsSnapshot(
    val total: Long = 0,
    val direct: Long = 0,
    val unblocked: Long = 0,
    val errors: Long = 0,
    val trafficIn: Long = 0,
    val trafficOut: Long = 0,
    val speedIn: Double = 0.0,
    val speedOut: Double = 0.0,
    val averageSpeedIn: Double = 0.0,
    val averageSpeedOut: Double = 0.0,
)

/** Thread-safe counters of the proxy server */
class ProxyStats {

    private val total = AtomicLong()
    private val direct = AtomicLong()
    private val unblocked = AtomicLong()
    private val errors = AtomicLong()
    private val trafficIn = AtomicLong()
    private val trafficOut = AtomicLong()

    private var lastIn = 0L
    private var lastOut = 0L
    private var lastTime = 0L
    private var speedIn = 0.0
    private var speedOut = 0.0
    private var speedInSum = 0.0
    private var speedInSamples = 0
    private var speedOutSum = 0.0
    private var speedOutSamples = 0

    fun countDirect() {
        total.incrementAndGet()
        direct.incrementAndGet()
    }

    fun countUnblocked() {
        total.incrementAndGet()
        unblocked.incrementAndGet()
    }

    fun countError() {
        total.incrementAndGet()
        errors.incrementAndGet()
    }

    fun addIncoming(bytes: Int) {
        trafficIn.addAndGet(bytes.toLong())
    }

    fun addOutgoing(bytes: Int) {
        trafficOut.addAndGet(bytes.toLong())
    }

    /** Recalculates speeds since the previous call and returns a snapshot. Called by one thread once a second */
    @Synchronized
    fun tick(): StatsSnapshot {
        val now = System.nanoTime()
        val incoming = trafficIn.get()
        val outgoing = trafficOut.get()

        if (lastTime != 0L) {
            val seconds = (now - lastTime) / 1e9
            if (seconds > 0) {
                speedIn = (incoming - lastIn) * 8 / seconds
                speedOut = (outgoing - lastOut) * 8 / seconds
                if (speedIn > 0) {
                    speedInSum += speedIn
                    speedInSamples++
                }
                if (speedOut > 0) {
                    speedOutSum += speedOut
                    speedOutSamples++
                }
            }
        }

        lastIn = incoming
        lastOut = outgoing
        lastTime = now

        return StatsSnapshot(
            total = total.get(),
            direct = direct.get(),
            unblocked = unblocked.get(),
            errors = errors.get(),
            trafficIn = incoming,
            trafficOut = outgoing,
            speedIn = speedIn,
            speedOut = speedOut,
            averageSpeedIn = if (speedInSamples > 0) speedInSum / speedInSamples else 0.0,
            averageSpeedOut = if (speedOutSamples > 0) speedOutSum / speedOutSamples else 0.0,
        )
    }
}
