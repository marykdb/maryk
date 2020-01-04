package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
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
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.Values
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.checkExistence
import maryk.datastore.rocksdb.processors.helpers.historicQualifierRetriever
import maryk.datastore.rocksdb.processors.helpers.nonHistoricQualifierRetriever
import maryk.datastore.rocksdb.processors.helpers.readValue

/**
 * Read values for [key] from an [iterator] to a ValuesWithMeta object.
 * Filter results on [select] and use [toVersion]
 */
internal fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.readTransactionIntoValuesWithMetaData(
    iterator: DBIterator,
    creationVersion: ULong,
    columnFamilies: TableColumnFamilies,
    key: Key<DM>,
    select: RootPropRefGraph<P>?,
    toVersion: ULong?
): ValuesWithMetaData<DM, P>? {
    var maxVersion = creationVersion
    var isDeleted = false

    val values: Values<DM, P> = if (toVersion == null) {
        checkExistence(iterator, key)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.nonHistoricQualifierRetriever(key)

        var index: Int
        this.convertStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, definition, valueWithVersionReader ->
                val currentVersion: ULong
                val value = when (storageType) {
                    ObjectDelete -> {
                        val valueBytes = iterator.value()
                        val value = if (iterator.key()[key.size] == 0.toByte()) {
                            isDeleted = valueBytes.last() == TRUE
                        } else null
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })
                        value
                    }
                    Value -> {
                        val valueBytes = iterator.value()
                        index = 0
                        val reader = { valueBytes[index++] }

                        currentVersion = initULong(reader)

                        readValue(definition, reader)  {
                            valueBytes.size - index
                        }
                    }
                    ListSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })
                        initIntByVar { valueBytes[index++] }
                    }
                    SetSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })
                        initIntByVar { valueBytes[index++] }
                    }
                    MapSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })
                        initIntByVar { valueBytes[index++] }
                    }
                    Embed -> {
                        val valueBytes = iterator.value()
                        index = 0
                        val reader = { valueBytes[index++] }

                        currentVersion = initULong(reader)
                    }
                    TypeValue -> throw StorageException("Not used in direct encoding")
                }
                maxVersion = maxOf(currentVersion, maxVersion)
                valueWithVersionReader(currentVersion, value)
            }
        )
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("No historic table present so cannot use `toVersion` on get")
        }

        checkExistence(iterator, key)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.historicQualifierRetriever(key, toVersion) { version ->
            maxVersion = maxOf(version, maxVersion)
        }

        var index: Int
        this.convertStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, definition, valueWithVersionReader ->
                val value = when (storageType) {
                    ObjectDelete -> {
                        if (iterator.key().last() == 0.toByte()) {
                            iterator.value().also {
                                isDeleted = it[0] == TRUE
                            }
                        } else null
                    }
                    Value -> {
                        val valueBytes = iterator.value()
                        index = 0
                        val reader = { valueBytes[index++] }

                        readValue(definition, reader)  {
                            valueBytes.size - index
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
                    Embed -> { Unit }
                    TypeValue -> throw StorageException("Not used in direct encoding")
                }
                valueWithVersionReader(creationVersion, value)
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
