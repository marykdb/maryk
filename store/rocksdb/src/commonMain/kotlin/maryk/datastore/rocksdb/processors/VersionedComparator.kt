package maryk.datastore.rocksdb.processors

import maryk.datastore.rocksdb.compareTo
import maryk.datastore.rocksdb.compareToWithOffsetAndLength
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.DirectSlice

private const val versionSize = ULong.SIZE_BYTES

/**
 * Takes care that qualifiers are first sorted on their reference/value and then on version
 * Otherwise the version bytes could make values come before their root qualifiers.
 */
internal class VersionedComparator(
    private val keySize: Int
) : maryk.rocksdb.DirectComparator(ComparatorOptions()) {
    override fun name() = "maryk.VersionedComparator"
    override fun compare(a: DirectSlice, b: DirectSlice): Int {
        return if (a.size() > keySize && b.size() > keySize) {
            when (val comparison =
                a.compareToWithOffsetAndLength(0, a.size() - versionSize, b, 0, b.size() - versionSize)) {
                0 -> a.compareToWithOffsetAndLength(
                    a.size() - versionSize,
                    versionSize,
                    b,
                    b.size() - versionSize,
                    versionSize
                )
                else -> comparison
            }
        } else {
            a.compareTo(b)
        }
    }
}
