package maryk.datastore.foundationdb

import kotlin.uuid.Uuid

/** UUID text has no zero bytes, so its canonical tuple string needs no escaping. */
internal fun packReceiptKey(prefix: ByteArray, session: ByteArray, requestId: Uuid): ByteArray {
    val bytes = requestId.toByteArray()
    return packReceiptKey(prefix, session) { bytes[it].toInt() and 0xff }
}

/** A monotonic receipt ordinal has the same UUID tuple bytes without temporary UUID/byte objects. */
internal fun packReceiptKey(prefix: ByteArray, session: ByteArray, ordinal: Long): ByteArray =
    packReceiptKey(prefix, session) { index ->
        if (index < Long.SIZE_BYTES) 0 else (ordinal ushr ((15 - index) * 8)).toInt() and 0xff
    }

private inline fun packReceiptKey(prefix: ByteArray, session: ByteArray, byteAt: (Int) -> Int): ByteArray =
    ByteArray(prefix.size + session.size + 38).also { key ->
        prefix.copyInto(key)
        session.copyInto(key, prefix.size)
        var offset = prefix.size + session.size
        key[offset++] = 2 // FoundationDB tuple UTF-8 string marker.
        for (index in 0 until 16) {
            if (index == 4 || index == 6 || index == 8 || index == 10) key[offset++] = 45
            val byte = byteAt(index)
            key[offset++] = UUID_HEX[byte ushr 4].code.toByte()
            key[offset++] = UUID_HEX[byte and 15].code.toByte()
        }
        // ByteArray is zero-initialized, including the tuple string terminator.
    }

private const val UUID_HEX = "0123456789abcdef"
