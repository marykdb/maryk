package maryk.lib.extensions.compare

private val MAX_BYTE = 0b1111_1111.toUByte()

/**
 * Compares ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
operator fun ByteArray.compareTo(other: ByteArray): Int {
    for (it in 0 until minOf(this.size, other.size)) {
        val a = this[it].toUByte() and MAX_BYTE
        val b = other[it].toUByte() and MAX_BYTE
        if (a != b) {
            return a.toUByte().toInt() - b.toUByte().toInt()
        }
    }
    return this.size - other.size
}

/**
 * Compares ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
fun ByteArray.compareWithOffsetTo(other: ByteArray, offset: Int): Int {
    val otherSize = other.size - offset
    for (it in 0 until minOf(this.size, otherSize)) {
        val a = this[it].toUByte() and MAX_BYTE
        val b = other[it + offset].toUByte() and MAX_BYTE
        if (a != b) {
            return a.toUByte().toInt() - b.toUByte().toInt()
        }
    }
    return this.size - otherSize
}

/**
 * Compares only defined bytes of ByteArray to [other] ByteArray.
 * Returns zero if this object is equal to the specified [other] object,
 * a negative number if it's less than [other],
 * or a positive number if it's greater than [other].
 */
fun ByteArray.compareDefinedTo(other: ByteArray, offset: Int): Int {
    val otherSize = other.size - offset
    for (it in 0 until minOf(this.size, otherSize)) {
        val a = this[it].toUByte() and MAX_BYTE
        val b = other[it + offset].toUByte() and MAX_BYTE
        if (a != b) {
            return a.toUByte().toInt() - b.toUByte().toInt()
        }
    }
    return if (otherSize < this.size){
        this.size - otherSize
    } else {
        0
    }
}

/**
 * Match given [bytes] to a part from index [fromOffset]
 * It will match in reverse order since that usage is faster in sorted lists
 */
fun ByteArray.matchPart(fromOffset: Int, bytes: ByteArray, fromLength: Int = this.size, offset: Int = 0, length: Int = bytes.size): Boolean {
    if (length > fromLength) return false
    for (index in length - 1 downTo 0) {
        if (this[index + fromOffset] != bytes[index + offset]) return false
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
        val v = newArray[i].toUByte()

        if (v < MAX_BYTE) {
            newArray[i] = (v + 1.toUByte()).toByte()
            break
        } else if (i == 0) {
            return this
            // All bytes are max bytes
        }
    }
    return newArray
}

/**
 * Lower the range with 1 byte while keeping length the same
 * Will throw IllegalStateException if byte array cannot be lowered further
 */
fun ByteArray.prevByteInSameLength(maxLengthToRead: Int? = null): ByteArray {
    val newArray = this.copyOf()
    val startIndex = maxLengthToRead?.let { newArray.lastIndex - it } ?: 0
    for (i in newArray.lastIndex downTo startIndex) {
        val v = newArray[i].toUByte()

        if (v > 0u) {
            newArray[i] = (v - 1.toUByte()).toByte()
            break
        } else if (i == 0) {
            throw IllegalStateException("Byte array already reached the end")
        } else {
            newArray[i] = 0xFF.toByte()
        }
    }
    return newArray
}
