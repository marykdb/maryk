package maryk.datastore.rocksdb.processors

import maryk.ByteBuffer
import maryk.datastore.rocksdb.compareToWithOffsetAndLength
import maryk.datastore.rocksdb.compareWith
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.rocksdb.ComparatorOptions

/**
 * Takes care that qualifiers are first sorted on their reference/value and then on version
 * Otherwise the version bytes could make values come before their root qualifiers.
 */
internal class VersionedComparator(
    comparatorOptions: ComparatorOptions,
    private val keySize: Int
) : maryk.rocksdb.AbstractComparator(comparatorOptions) {
    override fun name() = "maryk.VersionedComparator"
    override fun compare(a: ByteBuffer, b: ByteBuffer): Int {
        val aRemaining = a.remaining()
        val bRemaining = b.remaining()
        return if (aRemaining > keySize && bRemaining > keySize) {
            val aKeyLength = aRemaining - VERSION_BYTE_SIZE
            val bKeyLength = bRemaining - VERSION_BYTE_SIZE
            when (val comparison =
                a.compareToWithOffsetAndLength(0, aKeyLength, b, 0, bKeyLength)) {
                0 -> a.compareToWithOffsetAndLength(
                    aKeyLength,
                    VERSION_BYTE_SIZE,
                    b,
                    bKeyLength,
                    VERSION_BYTE_SIZE
                )
                else -> comparison
            }
        } else {
            a.compareWith(b)
        }
    }
}
