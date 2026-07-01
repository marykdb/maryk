package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.writeToStorage
import maryk.core.properties.definitions.IsComparableDefinition
import maryk.core.values.Values
import maryk.datastore.indexeddb.IndexedDbSensitiveFieldSupport
import maryk.datastore.indexeddb.createTableRowKey
import maryk.datastore.indexeddb.encodeStorageValue



internal suspend fun <DM : IsRootDataModel> createStoragePlan(
    dataModel: DM,
    modelId: UInt,
    keyBytes: ByteArray,
    values: Values<DM>,
    sensitiveFields: IndexedDbSensitiveFieldSupport,
): CurrentStateStoragePlan {
    val rows = mutableListOf<StorageRowToWrite>()
    val indexRows = mutableListOf<ByteArray>()
    val uniqueRows = mutableListOf<Triple<ByteArray, ByteArray, ByteArray>>()

    dataModel.Meta.indexes?.forEach { index ->
        index.toStorageByteArraysForIndex(values, keyBytes).forEach { valueAndKey ->
            indexRows += createIndexRowKey(index.referenceStorageByteArray.bytes, valueAndKey)
        }
    }

    values.writeToStorage { type, qualifier, definition, value ->
        if (type == ObjectDelete) return@writeToStorage

        val encodedValue = encodeStorageValue(type, definition, value)
        rows += StorageRowToWrite(qualifier, encodedValue, definition, type)
    }

    val encryptedTableRows = rows.map { row ->
        createTableRowKey(keyBytes, row.qualifier) to
            sensitiveFields.encryptValueIfSensitive(modelId, row.qualifier, row.encodedValue)
    }
    for (row in rows) {
        if (row.type == Value && row.definition is IsComparableDefinition<*, *> && row.definition.unique) {
            val uniqueValue = sensitiveFields.mapUniqueValueBytes(modelId, row.qualifier, row.encodedValue)
            val uniqueKey = createUniqueRowKey(row.qualifier, uniqueValue)
            uniqueRows += Triple(uniqueKey, keyBytes, row.qualifier)
        }
    }

    return CurrentStateStoragePlan(
        tableRows = encryptedTableRows,
        indexRows = indexRows,
        uniqueRows = uniqueRows,
    )
}
