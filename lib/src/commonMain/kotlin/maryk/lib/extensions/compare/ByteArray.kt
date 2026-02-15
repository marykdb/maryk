package maryk.lib.extensions.compare

/**
 * Compares ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
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
 * Compares ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
fun ByteArray.compareToWithOffsetLength(other: ByteArray, offset: Int, length: Int = other.size - offset): Int {
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
 * Compares only defined bytes of ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
fun ByteArray.compareDefinedTo(other: ByteArray, offset: Int = 0, length: Int = other.size - offset): Int {
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
 * Match given [bytes] to this byte array from index [fromOffset]
 * It will match in reverse order since that usage is faster in sorted lists
 */
fun ByteArray.match(fromOffset: Int, bytes: ByteArray, fromLength: Int = this.size, offset: Int = 0, length: Int = bytes.size): Boolean {
    if (length != fromLength) return false

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
 * Match given [bytes] to a part from index [fromOffset]
 * It will match in reverse order since that usage is faster in sorted lists
 */
fun ByteArray.matchPart(fromOffset: Int, bytes: ByteArray, fromLength: Int = this.size, offset: Int = 0, length: Int = bytes.size): Boolean {
    if (length > fromLength) return false

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
