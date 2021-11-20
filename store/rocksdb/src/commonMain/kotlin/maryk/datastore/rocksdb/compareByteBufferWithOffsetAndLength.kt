package maryk.datastore.rocksdb

import maryk.ByteBuffer

private val MAX_BYTE = 0b1111_1111.toUByte()

fun ByteBuffer.compareWith(b: ByteBuffer): Int {
    return this.compareToWithOffsetAndLength(0, this.remaining(), b, 0, b.remaining())
}

fun ByteBuffer.compareToWithOffsetAndLength(aOffset: Int, aLength: Int, b: ByteBuffer, bOffset: Int, bLength: Int): Int {
    for (it in 0 until minOf(aLength, bLength)) {
        val aByte = this[it + aOffset].toUByte() and MAX_BYTE
        val bByte = b[it + bOffset].toUByte() and MAX_BYTE
        if (aByte != bByte) {
            return aByte.toInt() - bByte.toInt()
        }
    }
    return aLength - bLength
}
