package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.extensions.bytes.initULong
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.EmptyValueItems
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
    toVersion: ULong?,
    cachedRead: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
): ValuesWithMetaData<DM, P>? {
    var maxVersion = creationVersion
    var isDeleted = false

    val values: Values<DM, P> = if (select != null && select.properties.isEmpty()) {
        // Don't read the values if no values are selected
        this.values(null) { EmptyValueItems }
    } else if (toVersion == null) {
        checkExistence(iterator, key)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.nonHistoricQualifierRetriever(key)

        var index: Int
        this.readStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, reference ->
                val currentVersion: ULong
                when (storageType) {
                    ObjectDelete -> {
                        if (iterator.key().last() == 0.toByte()) {
                            val value = iterator.value()
                            index = 0
                            currentVersion = maxOf(initULong({ value[index++] }), maxVersion)
                            isDeleted = value[index] == TRUE
                        } else {
                            currentVersion = 0uL
                        }
                        null
                    }
                    Value -> {
                        val valueBytes = iterator.value()
                        index = 0
                        val reader = { valueBytes[index++] }
                        currentVersion = initULong(reader)

                        cachedRead(reference, currentVersion) {
                            val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                ?: reference.propertyDefinition

                            readValue(definition, reader) {
                                valueBytes.size - index
                            }
                        }
                    }
                    ListSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })

                        cachedRead(reference, currentVersion) {
                            initIntByVar { valueBytes[index++] }
                        }
                    }
                    SetSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })

                        cachedRead(reference, currentVersion) {
                            initIntByVar { valueBytes[index++] }
                        }
                    }
                    MapSize -> {
                        val valueBytes = iterator.value()
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })

                        cachedRead(reference, currentVersion) {
                            initIntByVar { valueBytes[index++] }
                        }
                    }
                    Embed -> {
                        val valueBytes = iterator.value()
                        index = 0
                        currentVersion = initULong({ valueBytes[index++] })
                        Unit
                    }
                    TypeValue -> throw StorageException("Not used in direct encoding")
                }.also {
                    maxVersion = maxOf(currentVersion, maxVersion)
                }
            }
        )
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("No historic table present so cannot use `toVersion` on get")
        }

        checkExistence(iterator, key)

        var currentVersion: ULong = 0uL
        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.historicQualifierRetriever(key, toVersion) { version ->
            currentVersion = version
            maxVersion = maxOf(currentVersion, maxVersion)
        }

        var index: Int
        this.readStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, reference ->
                cachedRead(reference, currentVersion) {
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
                            val reader = { valueBytes[index++] }

                            val definition =
                                (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                    ?: reference.propertyDefinition
                            readValue(definition, reader) {
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
                        Embed -> {
                            Unit
                        }
                        TypeValue -> throw StorageException("Not used in direct encoding")
                    }
                }
            }
        )
    }

    // Return null if no values where found but values where selected
    if (values.size == 0 && (select == null || select.properties.isNotEmpty())) {
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
