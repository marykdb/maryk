package maryk.lib.extensions.compare

/**
 * Compares ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 *
 * Comparison is lexicographical on unsigned byte values (`0..255`), then on array size.
 *
 * Example:
 * - `byteArrayOf(1, 2) < byteArrayOf(1, 2, 0)`
 * - `byteArrayOf(0xFF.toByte()) > byteArrayOf(0x01)`
 */
infix operator fun ByteArray.compareTo(other: ByteArray): Int {
    val minSize = minOf(this.size, other.size)
    var index = 0
    while (index < minSize) {
        val a = this[index].toInt() and 0xFF
        val b = other[index].toInt() and 0xFF
        if (a != b) {
            return a - b
        }
        index++
    }
    return this.size - other.size
}

/**
 * Compares this byte array against a window in [other].
 *
 * [offset] points to the first byte in [other] to compare.
 * [length] defines the window size in [other] (default: `other.size - offset`).
 *
 * Returns:
 * - `0` when equal over compared bytes and same effective length
 * - `< 0` when this is lexicographically smaller
 * - `> 0` when this is lexicographically greater
 *
 * Comparison is lexicographical on unsigned byte values (`0..255`), then on
 * `this.size - length`.
 *
 * Contract:
 * - Caller must ensure `offset >= 0`, `length >= 0`, and `offset + length <= other.size`.
 * - No defensive bounds checks are performed for performance.
 *
 * Example:
 * - `byteArrayOf(2, 3).compareToRange(byteArrayOf(1, 2, 3, 4), 1, 2) == 0`
 */
fun ByteArray.compareToRange(other: ByteArray, offset: Int, length: Int = other.size - offset): Int {
    val minSize = minOf(this.size, length)
    var index = 0
    while (index < minSize) {
        val a = this[index].toInt() and 0xFF
        val b = other[index + offset].toInt() and 0xFF
        if (a != b) {
            return a - b
        }
        index++
    }
    return this.size - length
}

/**
 * Compares this byte array with up to [length] bytes from [other] starting at [offset].
 *
 * Like [compareToRange], but when compared prefix bytes are equal and [length] is
 * smaller than `this.size`, this returns a positive value (`this.size - length`).
 *
 * Returns:
 * - `0` when equal on compared bytes and [length] is not smaller than `this.size`
 * - `< 0` when this is lexicographically smaller
 * - `> 0` when this is lexicographically greater, or when equal prefix but `this` is longer
 *
 * Contract:
 * - Caller must ensure `offset >= 0`, `length >= 0`, and `offset + length <= other.size`.
 * - No defensive bounds checks are performed for performance.
 */
fun ByteArray.compareDefinedRange(other: ByteArray, offset: Int = 0, length: Int = other.size - offset): Int {
    val minSize = minOf(this.size, length)
    var index = 0
    while (index < minSize) {
        val a = this[index].toInt() and 0xFF
        val b = other[index + offset].toInt() and 0xFF
        if (a != b) {
            return a - b
        }
        index++
    }
    return if (length < this.size) this.size - length else 0
}

/**
 * Checks whether a range in this array exactly matches a range in [bytes].
 *
 * This compares [length] bytes, where:
 * - source range starts at [fromOffset] in `this`
 * - target range starts at [offset] in [bytes]
 *
 * [sourceLength] is expected size of the source range and must be equal to [length]
 * for a match to be possible.
 *
 * Contract:
 * - Caller must ensure compared ranges are within bounds.
 * - No defensive bounds checks are performed for performance.
 */
fun ByteArray.matchesRange(fromOffset: Int, bytes: ByteArray, sourceLength: Int = this.size, offset: Int = 0, length: Int = bytes.size): Boolean {
    if (length != sourceLength) return false

    var index = length - 1
    while (index >= 0) {
        if (this[index + fromOffset] != bytes[index + offset]) {
            return false
        }
        index--
    }
    return true
}

/**
 * Checks whether a range in this array starts with [length] bytes from [bytes].
 *
 * This compares [length] bytes, where:
 * - source range starts at [fromOffset] in `this`
 * - target range starts at [offset] in [bytes]
 *
 * Returns false when [length] is larger than [sourceLength].
 *
 * Contract:
 * - Caller must ensure compared ranges are within bounds.
 * - No defensive bounds checks are performed for performance.
 */
fun ByteArray.matchesRangePart(fromOffset: Int, bytes: ByteArray, sourceLength: Int = this.size, offset: Int = 0, length: Int = bytes.size): Boolean {
    if (length > sourceLength) return false

    var index = length - 1
    while (index >= 0) {
        if (this[index + fromOffset] != bytes[index + offset]) {
            return false
        }
        index--
    }
    return true
}

/**
 * Higher the range with 1 byte while keeping length the same
 * Will return same byte array if it cannot be highered
 */
fun ByteArray.nextByteInSameLength(): ByteArray {
    val newArray = this.copyOf()
    for (i in newArray.lastIndex downTo 0) {
        if (newArray[i] != 0xFF.toByte()) {
            newArray[i]++
            return newArray
        }
    }
    return this // All bytes are max bytes
}

/**
 * Lower the range with 1 byte while keeping length the same
 * Will throw IllegalStateException if byte array cannot be lowered further
 */
fun ByteArray.prevByteInSameLength(maxLengthToRead: Int? = null): ByteArray {
    val newArray = this.copyOf()
    val startIndex = maxLengthToRead?.let { newArray.size - it.coerceAtMost(newArray.size) } ?: 0
    for (i in newArray.lastIndex downTo startIndex) {
        if (newArray[i] != 0.toByte()) {
            newArray[i]--
            return newArray
        }
        newArray[i] = 0xFF.toByte()
    }
    throw IllegalStateException("Byte array already reached the minimum value")
}
