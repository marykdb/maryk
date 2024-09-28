package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.Transaction
import maryk.datastore.shared.TypeIndicator
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.compareToWithOffsetLength

internal fun unsetNonChangedValues(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    currentValues: List<Pair<ByteArray, ByteArray>>,
    qualifiersToKeep: List<ByteArray>,
    versionBytes: ByteArray
) {
    val sortedQualifiersToKeep = qualifiersToKeep.sortedWith { o1, o2 -> o1.compareTo(o2) }
    var minIndex = 0

    for ((qualifier, _) in currentValues) {
        val index = sortedQualifiersToKeep.binarySearch(fromIndex = minIndex) {
            it.compareToWithOffsetLength(qualifier, key.bytes.size, qualifier.size - key.bytes.size)
        }
        if (index < 0) {
            // Delete the value by setting it to the DeletedIndicator
            setValue(transaction, columnFamilies, qualifier, versionBytes, TypeIndicator.DeletedIndicator.byteArray)
        } else {
            // Start next time comparing with next value in qualifiersToKeep as they are ordered
            minIndex = index + 1
        }
    }
}
