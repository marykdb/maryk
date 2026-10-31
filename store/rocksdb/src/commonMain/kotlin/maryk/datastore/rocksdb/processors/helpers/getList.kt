package maryk.datastore.rocksdb.processors.helpers

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
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.shared.hasStoredValuePayload
import maryk.datastore.shared.readStoredCount
import maryk.datastore.shared.readValue
import maryk.lib.extensions.compare.matchesRangePart
import maryk.rocksdb.ReadOptions

/** Read the complete list subtree, including embedded and typed descendants. */
internal fun <T : Any> getList(
    dbAccessor: DBAccessor,
    columnFamilies: TableColumnFamilies,
    readOptions: ReadOptions,
    key: Key<*>,
    reference: TypedPropertyReference<out List<T>>
): MutableList<T> {
    val prefix = reference.toStorageByteArray(key.bytes)
    dbAccessor.getIterator(readOptions, columnFamilies.table).use { iterator ->
        iterator.seek(prefix)
        var currentValue = byteArrayOf()
        var currentQualifier = byteArrayOf()
        return readStorageToList(reference,
            getQualifier = { handler ->
                if (iterator.isValid() && iterator.key().matchesRangePart(0, prefix)) {
                    val storedKey = iterator.key()
                    val qualifier = storedKey.copyOfRange(key.bytes.size, storedKey.size)
                    currentQualifier = qualifier
                    currentValue = iterator.value()
                    requireVersionedValue(currentValue)
                    iterator.next()
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
                    Value -> dbAccessor.dataStore.withDecryptedValueIfNeeded(columnFamilies.modelId, key.bytes,
                        currentQualifier, currentValue, VERSION_BYTE_SIZE, currentValue.size - VERSION_BYTE_SIZE) { bytes, offset, length ->
                        var index = offset
                        val definition = (property.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition ?: property.propertyDefinition
                        readValue(definition, { bytes[index++] }) { offset + length - index }
                    }
                }
            },
        ) ?: mutableListOf()
    }
}

internal fun readStoredListCount(countBytes: ByteArray): Int {
    requireVersionedValue(countBytes)
    return readStoredCount(countBytes, VERSION_BYTE_SIZE, countBytes.size - VERSION_BYTE_SIZE)
        ?: throw StorageException("Stored list count is deleted")
}

internal fun requireVersionedValue(valueBytes: ByteArray) = requireVersionedValueSize(valueBytes.size)

internal fun requireVersionedValueSize(valueSize: Int) {
    if (valueSize < VERSION_BYTE_SIZE) throw StorageException("Stored value is missing version prefix: $valueSize bytes")
}
