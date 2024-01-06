package maryk.datastore.hbase.helpers

import maryk.core.extensions.bytes.writeBytes
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.ListReference
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.shared.TypeIndicator
import maryk.lib.extensions.compare.match
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result

/**
 * Set a list value in [put] for [reference] with a [newList].
 * With [originalCount] it is determined if items need to be deleted.
 * Returns true if changed
 */
internal fun <T : Any> setListValue(
    currentRowResult: Result,
    put: Put,
    reference: ListReference<T, *>,
    newList: List<T>,
    originalCount: Int,
): Boolean {
    val referenceAsBytes = reference.toStorageByteArray()
    @Suppress("UNCHECKED_CAST")
    val valueDefinition = reference.propertyDefinition.valueDefinition as IsStorageBytesEncodable<T>

    // Set the count if changed
    if (newList.size != originalCount) {
        put.addColumn(dataColumnFamily, referenceAsBytes, countValueAsBytes(newList.size))
    }

    // Where is last addition
    var changed = false

    val toDeleteCount = originalCount - newList.size
    if (toDeleteCount > 0) {
        for (i in 0..toDeleteCount) {
            var byteIndex = referenceAsBytes.size
            val refToDelete = referenceAsBytes.copyOf(byteIndex + 4)
            (i + newList.size).toUInt().writeBytes({
                refToDelete[byteIndex++] = it
            })
            put.addColumn(dataColumnFamily, refToDelete, TypeIndicator.DeletedIndicator.byteArray)
        }
        changed = true
    }

    // Walk all new values to store
    newList.forEachIndexed { index, item ->
        var byteIndex = referenceAsBytes.size
        val newRef = referenceAsBytes.copyOf(byteIndex + 4)
        index.toUInt().writeBytes({
            newRef[byteIndex++] = it
        })

        val oldResult = currentRowResult.getColumnLatestCell(dataColumnFamily, newRef)
        val newValue = valueDefinition.toStorageBytes(item, TypeIndicator.NoTypeIndicator.byte)

        // Only write when value is different
        if (oldResult == null || !newValue.match(0, oldResult.valueArray, oldResult.valueOffset, oldResult.valueLength)) {
            put.addColumn(dataColumnFamily, newRef, newValue)
            changed = true
        }
    }

    return changed
}
