package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.lib.extensions.compare.compareDefinedTo
import org.rocksdb.ReadOptions

/** Get the current incrementing map key for [reference] */
internal fun getCurrentIncMapKey(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: IncMapReference<Comparable<Any>, Any, IsPropertyContext>
): ByteArray {
    val referenceToCompareTo = reference.toStorageByteArray(key.bytes)

    dbAccessor.getIterator(readOptions, columnFamilies.table).use { iterator ->
        iterator.seek(referenceToCompareTo)

        if (iterator.isValid()) {
            iterator.next()

            val foundReference = iterator.key()
            if (referenceToCompareTo.compareDefinedTo(foundReference, 0) == 0) {
                return foundReference.copyOfRange(key.size, foundReference.size)
            }
        }

        // If nothing was found create a new reference
        val mapKeySize  = reference.propertyDefinition.definition.keyDefinition.byteSize
        return ByteArray(mapKeySize) { if (it == 0) mapKeySize.toByte() else 0xFF.toByte() }
    }
}
