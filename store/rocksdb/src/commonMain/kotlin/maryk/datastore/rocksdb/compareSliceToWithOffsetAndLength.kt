package maryk.datastore.rocksdb

import maryk.rocksdb.DirectSlice

private val MAX_BYTE = 0b1111_1111.toUByte()

fun DirectSlice.compareTo(b: DirectSlice): Int {
    return this.compareToWithOffsetAndLength(0, this.size(), b, 0, b.size())
}

fun DirectSlice.compareToWithOffsetAndLength(aOffset: Int, aLength: Int, b: DirectSlice, bOffset: Int, bLength: Int): Int {
    for (it in 0 until minOf(aLength, bLength)) {
        val aByte = this[it + aOffset].toUByte() and MAX_BYTE
        val bByte = b[it + bOffset].toUByte() and MAX_BYTE
        if (aByte != bByte) {
            return aByte.toUByte().toInt() - bByte.toUByte().toInt()
        }
    }
    return aLength - bLength
}
