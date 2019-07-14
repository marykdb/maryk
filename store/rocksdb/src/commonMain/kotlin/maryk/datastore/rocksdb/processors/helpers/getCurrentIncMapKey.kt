package maryk.datastore.rocksdb.processors.helpers

import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.rocksdb.ReadOptions

/** Get the current incrementing map key for [reference] from [values] */
@Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
internal fun getCurrentIncMapKey(
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    toVersion: ULong?,
    key: Key<*>,
    reference: IncMapReference<Comparable<Any>, Any, IsPropertyContext>
): ByteArray {
    val referenceToCompareTo = reference.toStorageByteArray(key.bytes)
//
//    val valueIndex = values.binarySearch {
//        it.reference.compareTo(referenceToCompareTo)
//    }
//
//    val nextValueReference = values.getOrNull(valueIndex + 1)?.reference
//
//    return if (nextValueReference != null && referenceToCompareTo.compareDefinedTo(nextValueReference, 0) == 0) {
//        nextValueReference
//    } else {
//        val mapKeySize  = reference.propertyDefinition.definition.keyDefinition.byteSize
//        ByteArray(mapKeySize) { if (it == 0) mapKeySize.toByte() else 0xFF.toByte() }
//    }
    TODO("INC MAP GET NEXT")
}
