package maryk.datastore.rocksdb.processors

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToChanges
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.rocksdb.DBAccessor
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.DBIterator
import maryk.datastore.rocksdb.HistoricTableColumnFamilies
import maryk.datastore.rocksdb.TableColumnFamilies
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.checkExistence
import maryk.datastore.rocksdb.processors.helpers.historicQualifierRetriever
import maryk.datastore.rocksdb.processors.helpers.isHistoricDeleteMarker
import maryk.datastore.rocksdb.processors.helpers.nonHistoricQualifierRetriever
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.rocksdb.ReadOptions
import maryk.datastore.shared.readValue
import maryk.datastore.shared.readStoredCount

/** Process values for [key] from transaction to a DataObjectWithChanges object */
internal fun <DM : IsRootDataModel> DM.readTransactionIntoObjectChanges(
    iterator: DBIterator,
    creationVersion: ULong,
    columnFamilies: TableColumnFamilies,
    key: Key<DM>,
    select: RootPropRefGraph<DM>?,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    sortingKey: ByteArray?,
    cachedRead: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?,
    dataStore: RocksDBDataStore,
    dbAccessor: DBAccessor,
    readOptions: ReadOptions,
): DataObjectVersionedChange<DM>? {
    val changes: List<VersionedChanges>
    val readValuesAtVersion = { version: ULong ->
        val snapshotVersion = if (columnFamilies is HistoricTableColumnFamilies) version else null
        val family = if (snapshotVersion != null) (columnFamilies as HistoricTableColumnFamilies).historic.table else columnFamilies.table
        dbAccessor.getIterator(readOptions, family).use { valueIterator ->
            requireNotNull(readTransactionIntoValuesWithMetaData(
                valueIterator, dataStore, creationVersion, columnFamilies, key, select, snapshotVersion,
                { _, _, reader -> reader() },
            )).values
        }
    }

    if (toVersion == null && maxVersions == 1u) {
        checkExistence(iterator, key)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.nonHistoricQualifierRetriever(key)

        var index: Int
        changes = this.readStorageToChanges(
            getQualifier = getQualifier,
            select = select,
            creationVersion = if (creationVersion >= fromVersion) creationVersion else null,
            readValuesAtVersion = readValuesAtVersion,
            processValue = { storageType, reference, valueWithVersionReader ->
                val currentVersion: ULong
                val value = when (storageType) {
                    ObjectDelete -> {
                        val valueBytes = iterator.value()
                        if (valueBytes.size != VERSION_BYTE_SIZE + 1) {
                            currentVersion = 0uL
                            null
                        } else {
                            currentVersion = valueBytes.readVersionBytes()

                            cachedRead(reference, currentVersion) {
                                if (currentVersion >= fromVersion && iterator.key()[key.size] == 0.toByte()) {
                                    valueBytes.last() == TRUE
                                } else null
                            }
                        }
                    }
                    Value -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                    ?: reference.propertyDefinition

                                dataStore.withDecryptedValueIfNeeded(
                                    columnFamilies.modelId, key.bytes,
                                    (reference as IsPropertyReference<*, *, *>).toStorageByteArray(),
                                    valueBytes, VERSION_BYTE_SIZE, valueBytes.size - VERSION_BYTE_SIZE,
                                ) { payload, offset, length ->
                                    index = offset
                                    readValue(definition, { payload[index++] }) {
                                        offset + length - index
                                    }
                                }
                            }
                        } else null
                    }
                    ListSize -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                readStoredCount(valueBytes, VERSION_BYTE_SIZE, valueBytes.size - VERSION_BYTE_SIZE)
                            }
                        } else null
                    }
                    SetSize -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                readStoredCount(valueBytes, VERSION_BYTE_SIZE, valueBytes.size - VERSION_BYTE_SIZE)
                            }
                        } else null
                    }
                    MapSize -> {
                        val valueBytes = iterator.value()
                        currentVersion = valueBytes.readVersionBytes()

                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                readStoredCount(valueBytes, VERSION_BYTE_SIZE, valueBytes.size - VERSION_BYTE_SIZE)
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
            creationVersion = if (creationVersion >= fromVersion) creationVersion else null,
            readValuesAtVersion = readValuesAtVersion,
            processValue = { storageType, reference, valueWithVersionReader ->
                if (currentVersion >= fromVersion) {
                    val value = cachedRead(reference, currentVersion) {
                        when (storageType) {
                            ObjectDelete -> {
                                if (iterator.key()[key.size] == SOFT_DELETE_INDICATOR) {
                                    val value = iterator.value()
                                    if (value.isNotEmpty()) value[0] == TRUE else true
                                } else null
                            }
                            Value -> {
                                val valueBytes = iterator.value()
                                if (valueBytes.isHistoricDeleteMarker()) {
                                    return@cachedRead null
                                }
                                val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                    ?: reference.propertyDefinition
                                dataStore.withDecryptedValueIfNeeded(
                                    columnFamilies.modelId, key.bytes,
                                    (reference as IsPropertyReference<*, *, *>).toStorageByteArray(),
                                    valueBytes, 0, valueBytes.size,
                                ) { payload, offset, length ->
                                    index = offset
                                    readValue(definition, { payload[index++] }) {
                                        offset + length - index
                                    }
                                }
                            }
                            ListSize -> {
                                val valueBytes = iterator.value()
                                if (valueBytes.isHistoricDeleteMarker(allowLegacyMarker = false)) {
                                    return@cachedRead null
                                }
                                index = 0
                                readStoredCount(valueBytes)
                            }
                            SetSize -> {
                                val valueBytes = iterator.value()
                                if (valueBytes.isHistoricDeleteMarker(allowLegacyMarker = false)) {
                                    return@cachedRead null
                                }
                                index = 0
                                readStoredCount(valueBytes)
                            }
                            MapSize -> {
                                val valueBytes = iterator.value()
                                if (valueBytes.isHistoricDeleteMarker(allowLegacyMarker = false)) {
                                    return@cachedRead null
                                }
                                index = 0
                                readStoredCount(valueBytes)
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
