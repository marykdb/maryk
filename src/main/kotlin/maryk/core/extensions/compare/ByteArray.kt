package maryk.core.extensions.compare

import maryk.core.extensions.bytes.MAX_BYTE
import kotlin.experimental.and

/**
 * Compares ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
internal fun ByteArray.compareTo(other: ByteArray): Int  {
    for (it in 0 until minOf(this.size, other.size)) {
        val a = this[it] and MAX_BYTE
        val b = other[it] and MAX_BYTE
        if (a != b) { return a - b }
    }
    return this.size - other.size
}
