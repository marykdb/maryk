package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.incrementingMapAllocationPrefix

/**
 * Get the current incrementing map key qualifier for [reference].
 * Returns a qualifier (without key) to be used by writeIncMapAdditionsToStorage.
 */
internal fun getCurrentIncMapKey(
    tr: Transaction,
    tableDirs: IsTableDirectories,
    key: Key<*>,
    reference: IncMapReference<Comparable<Any>, Any, IsPropertyContext>
): ByteArray {
    val refBytes = reference.toStorageByteArray()
    val base = packKey(tableDirs.tablePrefix, key.bytes)
    val prefix = packKey(tableDirs.tablePrefix, key.bytes, refBytes)
    val maximum = readIncrementingMapMaximum(tr, tableDirs, key.bytes, reference)
    val iter = tr.getRange(Range.startsWith(prefix)).iterator()
    return if (iter.hasNext()) {
        // First is count; advance
        val first = iter.nextBlocking()
        val firstItemKey = if (!first.key.contentEquals(prefix)) first.key else if (iter.hasNext()) iter.nextBlocking().key else null
        if (firstItemKey != null) {
            incrementingMapAllocationPrefix(reference, firstItemKey.copyOfRange(base.size, firstItemKey.size), maximum)
        } else {
            // No items yet; create a starting qualifier [refBytes + keySizeMarker + 0xFF..]
            incrementingMapAllocationPrefix(reference, null, maximum)
        }
    } else {
        incrementingMapAllocationPrefix(reference, null, maximum)
    }
}
