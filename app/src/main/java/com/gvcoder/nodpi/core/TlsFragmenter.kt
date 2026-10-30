package com.gvcoder.nodpi.core

/**
 * Fragmentation of a TLS ClientHello, identical to the desktop NoDPI:
 * the record is split into the part before SNI, the SNI itself in 2-byte pieces
 * and the part after SNI, each wrapped into its own TLS record (`16 03 04 len`)
 */
object TlsFragmenter {

    const val RECORD_HEADER_SIZE = 5
    const val HANDSHAKE_CONTENT_TYPE: Byte = 0x16

    private const val SNI_CHUNK = 2

    /**
     * Finds the server name inside the ClientHello [body] (record without its 5-byte header).
     * Looks for the `server_name` extension by its zero bytes and consistent lengths
     */
    fun findSni(body: ByteArray): IntRange? {
        var i = 0
        while (i < body.size - 8) {
            if (body[i].toInt() == 0 && body[i + 1].toInt() == 0 && body[i + 2].toInt() == 0 &&
                body[i + 4].toInt() == 0 && body[i + 6].toInt() == 0 && body[i + 7].toInt() == 0
            ) {
                val extLen = body[i + 3].toInt() and 0xFF
                val listLen = body[i + 5].toInt() and 0xFF
                val nameLen = body[i + 8].toInt() and 0xFF
                if (extLen - listLen == 2 && listLen - nameLen == 3) {
                    val start = i + 9
                    val end = start + nameLen
                    if (end <= body.size) {
                        return start until end
                    }
                }
            }
            i++
        }
        return null
    }

    /**
     * Splits the record into TLS records ready to be sent. Without SNI the original
     * record ([header] + [body]) is returned as the only part
     */
    fun fragment(header: ByteArray, body: ByteArray): List<ByteArray> {
        val sni = findSni(body) ?: return listOf(header + body)

        val parts = ArrayList<ByteArray>()
        parts += record(body, 0, sni.first)
        var i = sni.first
        while (i <= sni.last) {
            val end = minOf(i + SNI_CHUNK, sni.last + 1)
            parts += record(body, i, end)
            i = end
        }
        parts += record(body, sni.last + 1, body.size)
        return parts
    }

    private fun record(source: ByteArray, from: Int, to: Int): ByteArray {
        val length = to - from
        val out = ByteArray(RECORD_HEADER_SIZE + length)
        out[0] = HANDSHAKE_CONTENT_TYPE
        out[1] = 0x03
        out[2] = 0x04
        out[3] = (length ushr 8).toByte()
        out[4] = length.toByte()
        System.arraycopy(source, from, out, RECORD_HEADER_SIZE, length)
        return out
    }
}

/** Concatenates parts into a single array */
fun List<ByteArray>.join(): ByteArray {
    val out = ByteArray(sumOf { it.size })
    var offset = 0
    for (part in this) {
        System.arraycopy(part, 0, out, offset, part.size)
        offset += part.size
    }
    return out
}
