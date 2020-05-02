package maryk.datastore.rocksdb.processors

import maryk.ByteBuffer
import maryk.datastore.rocksdb.compareToWithOffsetAndLength
import maryk.datastore.rocksdb.compareWith
import maryk.rocksdb.ComparatorOptions

private const val versionSize = ULong.SIZE_BYTES

/**
 * Takes care that qualifiers are first sorted on their reference/value and then on version
 * Otherwise the version bytes could make values come before their root qualifiers.
 */
internal class VersionedComparator(
    private val comparatorOptions: ComparatorOptions,
    private val keySize: Int
) : maryk.rocksdb.AbstractComparator(comparatorOptions) {
    override fun name() = "maryk.VersionedComparator"
    override fun compare(a: ByteBuffer, b: ByteBuffer): Int {
        return if (a.remaining() > keySize && b.remaining() > keySize) {
            when (val comparison =
                a.compareToWithOffsetAndLength(0, a.remaining() - versionSize, b, 0, b.remaining() - versionSize)) {
                0 -> a.compareToWithOffsetAndLength(
                    a.remaining() - versionSize,
                    versionSize,
                    b,
                    b.remaining() - versionSize,
                    versionSize
                )
                else -> comparison
            }
        } else {
            a.compareWith(b)
        }
    }
}
