@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.lib.extensions.compare

private val MAX_BYTE: UByte = 0b1111_1111.toUByte()

/**
 * Compares initByteArrayByHex to [other] initByteArrayByHex.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
operator fun ByteArray.compareTo(other: ByteArray): Int {
    for (it in 0 until minOf(this.size, other.size)) {
        val a = this[it].toUByte() and MAX_BYTE
        val b = other[it].toUByte() and MAX_BYTE
        if (a != b) {
            return (a.toUByte() - b.toUByte()).toInt()
        }
    }
    return this.size - other.size
}
