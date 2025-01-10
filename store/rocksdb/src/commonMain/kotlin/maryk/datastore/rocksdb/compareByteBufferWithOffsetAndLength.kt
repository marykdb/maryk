package maryk.datastore.rocksdb

import maryk.ByteBuffer

fun ByteBuffer.compareWith(b: ByteBuffer): Int =
    compareToWithOffsetAndLength(0, remaining(), b, 0, b.remaining())

fun ByteBuffer.compareToWithOffsetAndLength(aOffset: Int, aLength: Int, b: ByteBuffer, bOffset: Int, bLength: Int): Int {
    val minLength = minOf(aLength, bLength)
    for (it in 0 until minLength) {
        val aByte = get(it + aOffset).toUByte()
        val bByte = b[it + bOffset].toUByte()
        val diff = aByte.toInt() - bByte.toInt()
        if (diff != 0) return diff
    }
    return aLength - bLength
}
