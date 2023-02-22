package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToChanges
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.checkExistence
import maryk.datastore.rocksdb.processors.helpers.historicQualifierRetriever
import maryk.datastore.rocksdb.processors.helpers.nonHistoricQualifierRetriever
import maryk.datastore.rocksdb.processors.helpers.readValue
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes

/** Process values for [key] from transaction to a DataObjectWithChanges object */
internal fun <DM : IsRootValuesDataModel<P>, P : IsValuesPropertyDefinitions> DM.readTransactionIntoObjectChanges(
    iterator: DBIterator,
    creationVersion: ULong,
    columnFamilies: TableColumnFamilies,
    key: Key<DM>,
    select: RootPropRefGraph<P>?,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    sortingKey: ByteArray?,
    cachedRead: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
): DataObjectVersionedChange<DM>? {
    val changes: List<VersionedChanges>

    if (toVersion == null && maxVersions == 1u) {
        checkExistence(iterator, key)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.nonHistoricQualifierRetriever(key)

        var index: Int
        changes = this.readStorageToChanges(
            getQualifier = getQualifier,
            select = select,
            creationVersion = if (creationVersion > fromVersion) creationVersion else null,
            processValue = { storageType, reference, valueWithVersionReader ->
                val currentVersion: ULong
                val value = when (storageType) {
                    ObjectDelete -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        cachedRead(reference, currentVersion) {
                            if (currentVersion >= fromVersion && iterator.key()[key.size] == 0.toByte()) {
                                valueBytes.last() == TRUE
                            } else null
                        }
                    }
                    Value -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                    ?: reference.propertyDefinition

                                index = VERSION_BYTE_SIZE
                                val reader = { valueBytes[index++] }

                                readValue(definition, reader) {
                                    valueBytes.size - index
                                }
                            }
                        } else null
                    }
                    ListSize -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                index = VERSION_BYTE_SIZE
                                initIntByVar { valueBytes[index++] }
                            }
                        } else null
                    }
                    SetSize -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                index = VERSION_BYTE_SIZE
                                initIntByVar { valueBytes[index++] }
                            }
                        } else null
                    }
                    MapSize -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                index = VERSION_BYTE_SIZE
                                initIntByVar { valueBytes[index++] }
                            }
                        } else null
                    }
                    Embed -> {
                        currentVersion = iterator.value().readVersionBytes()
                        null
                    }
                    TypeValue -> throw StorageException("Not used in direct encoding")
                }
                if (currentVersion >= fromVersion) {
                    valueWithVersionReader(currentVersion, value)
                }
            }
        )
    } else {
        if (columnFamilies !is HistoricTableColumnFamilies) {
            throw RequestException("No historic table present so cannot use `toVersion` on get changes")
        }

        checkExistence(iterator, key)

        var currentVersion: ULong = creationVersion

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.historicQualifierRetriever(key, toVersion ?: ULong.MAX_VALUE, maxVersions) { version ->
            currentVersion = version
        }

        var index: Int
        changes = this.readStorageToChanges(
            getQualifier = getQualifier,
            select = select,
            creationVersion = if (creationVersion > fromVersion) creationVersion else null,
            processValue = { storageType, reference, valueWithVersionReader ->
                if (currentVersion >= fromVersion) {
                    val value = cachedRead(reference, currentVersion) {
                        when (storageType) {
                            ObjectDelete -> {
                                if (iterator.key().last() == 0.toByte()) {
                                    val value = iterator.value()
                                    value[0] == TRUE
                                } else null
                            }
                            Value -> {
                                val valueBytes = iterator.value()
                                index = 0
                                val reader = { valueBytes[index++] }

                                val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
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
                            Embed -> {}
                            TypeValue -> throw StorageException("Not used in direct encoding")
                        }
                    }
                    valueWithVersionReader(currentVersion, value)
                }
            }
        )
    }

    if (changes.isEmpty()) {
        // Return null if no ValueItems were found
        return null
    }
    return DataObjectVersionedChange(
        key = key,
        sortingKey = sortingKey?.let(::Bytes),
        changes = changes
    )
}
