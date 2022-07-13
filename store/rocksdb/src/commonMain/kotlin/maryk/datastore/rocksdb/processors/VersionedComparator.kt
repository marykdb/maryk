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
@Suppress("CanBeParameter")
internal class VersionedComparator(
    private val comparatorOptions: ComparatorOptions,
    private val keySize: Int
) : maryk.rocksdb.AbstractComparator(comparatorOptions) {
    override fun name() = "maryk.VersionedComparator"
    override fun compare(a: ByteBuffer, b: ByteBuffer): Int {
        return if (a.remaining() > keySize && b.remaining() > keySize) {
            when (val comparison =
                a.compareToWithOffsetAndLength(0, a.remaining() - VERSION_BYTE_SIZE, b, 0, b.remaining() - VERSION_BYTE_SIZE)) {
                0 -> a.compareToWithOffsetAndLength(
                    a.remaining() - VERSION_BYTE_SIZE,
                    VERSION_BYTE_SIZE,
                    b,
                    b.remaining() - VERSION_BYTE_SIZE,
                    VERSION_BYTE_SIZE
                )
                else -> comparison
            }
        } else {
            a.compareWith(b)
        }
    }
}
