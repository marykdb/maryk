package maryk.datastore.rocksdb.processors

import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToWithOffsetAndLength
import maryk.rocksdb.ComparatorOptions
import maryk.rocksdb.Slice

private const val versionSize = ULong.SIZE_BYTES

/**
 * Takes care that qualifiers are first sorted on their reference/value and then on version
 * Otherwise the version bytes could make values come before their root qualifiers.
 */
class VersionedComparator(
    val keySize: Int
) : maryk.rocksdb.Comparator(ComparatorOptions()) {
    override fun name() = "maryk.VersionedComparator"
    override fun compare(a: Slice, b: Slice): Int {
        val aBytes = a.data()
        val bBytes = b.data()
        return if (aBytes.size > keySize && bBytes.size > keySize) {
            when (val comparison =
                aBytes.compareToWithOffsetAndLength(0, aBytes.size - versionSize, bBytes, 0, bBytes.size - versionSize)) {
                0 -> aBytes.compareToWithOffsetAndLength(
                    aBytes.size - versionSize,
                    versionSize,
                    bBytes,
                    bBytes.size - versionSize,
                    versionSize
                )
                else -> comparison
            }
        } else {
            aBytes.compareTo(bBytes)
        }
    }
}
