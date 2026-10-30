package com.gvcoder.nodpi.ui

import java.util.Locale

/** Size in bytes, binary units as in the desktop statistics */
fun formatSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return String.format(Locale.US, "%.1f %s", size, units[unit])
}

/** Speed in bits per second, decimal units as in the desktop statistics */
fun formatSpeed(bitsPerSecond: Double): String {
    if (bitsPerSecond <= 0) {
        return "0 b/s"
    }
    val units = arrayOf("b/s", "Kb/s", "Mb/s", "Gb/s")
    var speed = bitsPerSecond
    var unit = 0
    while (speed >= 1000 && unit < units.lastIndex) {
        speed /= 1000
        unit++
    }
    return String.format(Locale.US, "%.0f %s", speed, units[unit])
}
