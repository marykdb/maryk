package maryk.datastore.rocksdb.processors.helpers

import maryk.core.extensions.bytes.toVarBytes
import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.ListReference
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.NO_TYPE_INDICATOR
import maryk.datastore.rocksdb.Transaction

/**
 * Set a list value in [transaction] for [reference] with a [newList] at new [version].
 * With [originalCount] it is determined if items need to be deleted.
 * Use [keepAllVersions] on true to keep old versions
 * Returns true if changed
 */
internal fun <T : Any> setListValue(
    transaction: Transaction,
    columnFamilies: TableColumnFamilies,
    key: Key<*>,
    reference: ListReference<T, *>,
    newList: List<T>,
    originalCount: Int,
    version: ByteArray
): Boolean {
    val keyAndReferenceAsBytes = reference.toStorageByteArray(key.bytes)
    @Suppress("UNCHECKED_CAST")
    val valueDefinition = reference.propertyDefinition.valueDefinition as IsStorageBytesEncodable<T>

    // Set the count
    setValue(transaction, columnFamilies, keyAndReferenceAsBytes, version, newList.size.toVarBytes())

    // Where is last addition
    var changed = false

    val toDeleteCount = originalCount - newList.size
    if (toDeleteCount > 0) {
        for (i in 0..toDeleteCount) {
            var byteIndex = keyAndReferenceAsBytes.size
            val refToDelete = keyAndReferenceAsBytes.copyOf(byteIndex + 4)
            (i + newList.size).toUInt().writeBytes({
                refToDelete[byteIndex++] = it
            })
            deleteValue(transaction, columnFamilies, refToDelete, version)
        }
        changed = true
    }

    // Walk all new values to store
    newList.forEachIndexed { index, item ->
        var byteIndex = keyAndReferenceAsBytes.size
        val newRef = keyAndReferenceAsBytes.copyOf(byteIndex + 4)
        index.toUInt().writeBytes({
            newRef[byteIndex++] = it
        })

        setValue(transaction, columnFamilies, newRef, version, valueDefinition.toStorageBytes(item, NO_TYPE_INDICATOR))
        changed = true
    }

    return changed
}
