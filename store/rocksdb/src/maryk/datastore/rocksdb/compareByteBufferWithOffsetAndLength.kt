package maryk.datastore.rocksdb

import maryk.ByteBuffer

fun ByteBuffer.compareWith(b: ByteBuffer): Int =
    compareToWithOffsetAndLength(0, remaining(), b, 0, b.remaining())

fun ByteBuffer.compareToWithOffsetAndLength(aOffset: Int, aLength: Int, b: ByteBuffer, bOffset: Int, bLength: Int): Int {
    var aIndex = aOffset
    var bIndex = bOffset
    val endIndex = aOffset + minOf(aLength, bLength)
    while (aIndex < endIndex) {
        val diff = (this[aIndex].toInt() and 0xFF) -
            (b[bIndex].toInt() and 0xFF)
        if (diff != 0) return diff
        aIndex++
        bIndex++
    }
    return aLength - bLength
}
