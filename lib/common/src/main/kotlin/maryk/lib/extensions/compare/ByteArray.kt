package maryk.lib.extensions.compare

import kotlin.experimental.and

private const val MAX_BYTE: Byte = 0b1111_1111.toByte()

/**
 * Compares initByteArrayByHex to [other] initByteArrayByHex.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
fun ByteArray.compareTo(other: ByteArray): Int {
    for (it in 0 until minOf(this.size, other.size)) {
        val a = this[it] and MAX_BYTE
        val b = other[it] and MAX_BYTE
        if (a != b) { return a - b }
    }
    return this.size - other.size
}
