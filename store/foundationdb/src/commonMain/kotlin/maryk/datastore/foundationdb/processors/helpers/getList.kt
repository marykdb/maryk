package maryk.datastore.foundationdb.processors.helpers

import maryk.core.exceptions.StorageException
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToList
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.references.TypedPropertyReference
import maryk.core.properties.types.Key
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.shared.hasStoredValuePayload
import maryk.datastore.shared.readStoredCount
import maryk.datastore.shared.readValue
import maryk.foundationdb.Range
import maryk.foundationdb.Transaction

/** Read the complete list subtree, including embedded and typed descendants. */
internal fun <T : Any> Transaction.getList(
    tableDirs: IsTableDirectories,
    key: Key<*>,
    reference: TypedPropertyReference<out List<T>>,
    decryptValue: DecryptValue? = null,
): MutableList<T> {
    val prefix = packKey(tableDirs.tablePrefix, key.bytes, reference.toStorageByteArray())
    val qualifierOffset = prefix.size - reference.calculateStorageByteLength()
    val iterator = getRange(Range.startsWith(prefix)).iterator()
    var currentValue = byteArrayOf()
    var currentQualifier = byteArrayOf()
    return readStorageToList(reference,
        getQualifier = { handler ->
            if (iterator.hasNext()) {
                val row = iterator.nextBlocking()
                val qualifier = row.key.copyOfRange(qualifierOffset, row.key.size)
                currentQualifier = qualifier
                currentValue = row.value
                requireVersionedValue(currentValue)
                handler({ qualifier[it] }, qualifier.size)
                true
            } else false
        },
        processValue = { type, property ->
            when (type) {
                ObjectDelete -> null
                ListSize, SetSize, MapSize -> readStoredCount(currentValue, VERSION_BYTE_SIZE, currentValue.size - VERSION_BYTE_SIZE)
                Embed -> if (hasStoredValuePayload(property.propertyDefinition, currentValue, VERSION_BYTE_SIZE, currentValue.size - VERSION_BYTE_SIZE)) Unit else null
                TypeValue -> throw StorageException("Not used in direct encoding")
                Value -> currentValue.withCurrentPayload(decryptValue, tableDirs.modelId, key.bytes, currentQualifier) { bytes, offset, length ->
                    var index = offset
                    val definition = (property.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition ?: property.propertyDefinition
                    readValue(definition, { bytes[index++] }) { offset + length - index }
                }
            }
        },
    ) ?: mutableListOf()
}

internal fun readStoredListCount(countValue: ByteArray): Int {
    requireVersionedValue(countValue)
    return readStoredCount(countValue, VERSION_BYTE_SIZE, countValue.size - VERSION_BYTE_SIZE)
        ?: throw StorageException("Stored list count is deleted")
}

internal fun requireVersionedValue(valueBytes: ByteArray) = requireVersionedValueSize(valueBytes.size)

internal fun requireVersionedValueSize(valueSize: Int) {
    if (valueSize < VERSION_BYTE_SIZE) throw StorageException("Stored value is missing version prefix: $valueSize bytes")
}
