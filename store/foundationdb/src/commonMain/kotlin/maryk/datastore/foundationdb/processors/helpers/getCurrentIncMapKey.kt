package maryk.datastore.foundationdb.processors.helpers

import maryk.foundationdb.Range
import maryk.foundationdb.Transaction
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.references.IncMapReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories

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
    val iter = tr.getRange(Range.startsWith(prefix)).iterator()
    return if (iter.hasNext()) {
        // First is count; advance
        iter.nextBlocking()
        if (iter.hasNext()) {
            val firstItemKey = iter.nextBlocking().key
            firstItemKey.copyOfRange(base.size, firstItemKey.size)
        } else {
            // No items yet; create a starting qualifier [refBytes + keySizeMarker + 0xFF..]
            val refSize = refBytes.size
            val mapKeySize = reference.propertyDefinition.definition.keyDefinition.byteSize
            ByteArray(mapKeySize + refSize + 1) { i -> if (i < refSize) refBytes[i] else if (i == refSize) mapKeySize.toByte() else 0xFF.toByte() }
        }
    } else {
        val refSize = refBytes.size
        val mapKeySize = reference.propertyDefinition.definition.keyDefinition.byteSize
        ByteArray(mapKeySize + refSize + 1) { i -> if (i < refSize) refBytes[i] else if (i == refSize) mapKeySize.toByte() else 0xFF.toByte() }
    }
}
