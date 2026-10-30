package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.shared.incrementingMapAllocationPrefix
import maryk.lib.extensions.compare.compareDefinedRange
import maryk.rocksdb.ReadOptions

/** Get the current incrementing map key for [reference] */
internal fun getCurrentIncMapKey(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: IncMapReference<Comparable<Any>, Any, IsPropertyContext>
): ByteArray {
    val referenceToCompareTo = reference.toStorageByteArray(key.bytes)
    val maximum = readIncrementingMapMaximum(dbAccessor, columnFamilies, key.bytes, reference)

    dbAccessor.getIterator(readOptions, columnFamilies.table).use { iterator ->
        iterator.seek(referenceToCompareTo)

        if (iterator.isValid()) {
            if (iterator.key().contentEquals(referenceToCompareTo)) iterator.next()

            if (iterator.isValid()) {
                val foundReference = iterator.key()
                if (referenceToCompareTo.compareDefinedRange(foundReference, 0) == 0) {
                    return incrementingMapAllocationPrefix(reference,
                        foundReference.copyOfRange(key.size, foundReference.size), maximum)
                }
            }
        }

        return incrementingMapAllocationPrefix(reference, null, maximum)
    }
}
