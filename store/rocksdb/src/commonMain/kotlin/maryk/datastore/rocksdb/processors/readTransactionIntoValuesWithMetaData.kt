package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.RequestException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.convertStorageToValues
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.properties.types.TypedValue
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.historicQualifierRetriever
import maryk.datastore.rocksdb.processors.helpers.nonHistoricQualifierRetriever
import maryk.rocksdb.ReadOptions
import maryk.rocksdb.RocksIterator
import maryk.rocksdb.Transaction

/**
 * Read values for [key] from a [transaction] with [readOptions] from [columnFamilies]
 * to a ValuesWithMeta object. Filter results on [select] and use [toVersion]
 */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.readTransactionIntoValuesWithMetaData(
    transaction: Transaction,
    readOptions: ReadOptions,
    creationVersion: ULong,
    columnFamilies: TableColumnFamilies,
    key: Key<DM>,
    select: RootPropRefGraph<P>?,
    toVersion: ULong?
): ValuesWithMetaData<DM, P>? {
    var maxVersion = creationVersion
    var isDeleted = false

    val values: Values<DM, P> = if (toVersion == null) {
        val iterator = transaction.getIterator(readOptions, columnFamilies.table)

        checkExistence(iterator, key)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.nonHistoricQualifierRetriever(key)

        var index: Int
        this.convertStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, definition ->
                when (storageType) {
                    ObjectDelete -> {
                        if (iterator.key().last() == 0.toByte()) {
                            val value = iterator.value()
                            index = 0
                            maxVersion = maxOf(initULong({ value[index++] }), maxVersion)
                            isDeleted = value[index] == TRUE
                        }
                        null
                    }
                    Value -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        Value.castDefinition(definition).readStorageBytes(valueBytes.size - index) {
                            valueBytes[index++]
                        }
                    }
                    ListSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        initIntByVar { valueBytes[index++] }
                    }
                    SetSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        initIntByVar { valueBytes[index++] }
                    }
                    MapSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        maxVersion = maxOf(initULong({ valueBytes[index++] }), maxVersion)

                        initIntByVar { valueBytes[index++] }
                    }
                    TypeValue -> {
                        val valueBytes = iterator.value()
                        index = 0
                        val reader = { valueBytes[index++] }

                        maxVersion = maxOf(initULong(reader), maxVersion)
                        val typeDefinition = TypeValue.castDefinition(definition)

                        val indicatorByte = reader()

                        val type = typeDefinition.typeEnum.readStorageBytes(typeDefinition.typeEnum.byteSize, reader)

                        if (indicatorByte == COMPLEX_TYPE_INDICATOR) {
                            TypedValue(type, Unit)
                        } else {
                            val valueDefinition = typeDefinition.definition(type) as IsSimpleValueDefinition<*, *>
                            valueDefinition.readStorageBytes(valueBytes.size - index, reader)
                        }
                    }
                    Embed -> { Unit }
                }
            }
        ).also {
            iterator.close()
        }
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("No historic table present so cannot use `toVersion`")
        }

        val iterator = transaction.getIterator(readOptions, columnFamilies.historic.table)

        checkExistence(iterator, key)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.historicQualifierRetriever(key, toVersion) { version ->
            maxVersion = maxOf(version, maxVersion)
        }

        var index: Int
        this.convertStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, definition ->
                when (storageType) {
                    ObjectDelete -> {
                        if (iterator.key().last() == 0.toByte()) {
                            val value = iterator.value()
                            isDeleted = value[0] == TRUE
                        }
                        null
                    }
                    Value -> {
                        val valueBytes = iterator.value()
                        index = 0
                        Value.castDefinition(definition).readStorageBytes(valueBytes.size) {
                            valueBytes[index++]
                        }
                    }
                    ListSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        initIntByVar { valueBytes[index++] }
                    }
                    SetSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        initIntByVar { valueBytes[index++] }
                    }
                    MapSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        initIntByVar { valueBytes[index++] }
                    }
                    TypeValue -> {
                        val valueBytes = iterator.value()
                        index = 0
                        val reader = { valueBytes[index++] }

                        val typeDefinition = TypeValue.castDefinition(definition)

                        val indicatorByte = reader()
                        val type = typeDefinition.typeEnum.readStorageBytes(typeDefinition.typeEnum.byteSize, reader)

                        if (indicatorByte == COMPLEX_TYPE_INDICATOR) {
                            TypedValue(type, Unit)
                        } else {
                            val valueDefinition = typeDefinition.definition(type) as IsSimpleValueDefinition<*, *>
                            valueDefinition.readStorageBytes(valueBytes.size - index, reader)
                        }
                    }
                    Embed -> { Unit }
                }
            }
        )
    }

    if (values.size == 0) {
        // Return null if no ValueItems were found
        return null
    }

    return ValuesWithMetaData(
        key = key,
        values = values,
        isDeleted = isDeleted,
        firstVersion = creationVersion,
        lastVersion = maxVersion
    )
}

/** Check existence of the [key] on [iterator] by checking existence of creation time */
private fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> checkExistence(
    iterator: RocksIterator,
    key: Key<DM>
) {
    // Start at begin of record
    iterator.seek(key.bytes)
    if (!iterator.isValid()) {
        // Is already past key so key does not exist
        // Should not happen since this needs to be checked before
        throw Exception("Key does not exist while it should have existed")
    }

    val creationDateKey = iterator.key()
    if (!key.bytes.contentEquals(creationDateKey)) {
        // Is already past key so key does not exist
        // Should not happen since this needs to be checked before
        throw Exception("Key does not exist while it should have existed")
    }
}
