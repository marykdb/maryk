package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
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
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.changes.VersionedChanges
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.IsTableDirectories
import maryk.datastore.foundationdb.processors.helpers.FDBIterator
import maryk.datastore.foundationdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.foundationdb.processors.helpers.checkExistence
import maryk.datastore.foundationdb.processors.helpers.historicQualifierRetriever
import maryk.datastore.foundationdb.processors.helpers.nonHistoricQualifierRetriever
import maryk.datastore.foundationdb.processors.helpers.packKey
import maryk.datastore.foundationdb.processors.helpers.readVersionBytes
import maryk.datastore.shared.readValue
import maryk.foundationdb.Range as FDBRange

/** Process values for [key] from FDB transaction to a DataObjectVersionedChange */
internal fun <DM : IsRootDataModel> DM.readTransactionIntoObjectChanges(
    tr: Transaction,
    creationVersion: ULong,
    tableDirs: IsTableDirectories,
    key: Key<DM>,
    select: RootPropRefGraph<DM>?,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    sortingKey: ByteArray?,
    cachedRead: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
): DataObjectVersionedChange<DM>? {
    val changes: List<VersionedChanges>

    if (toVersion == null && maxVersions == 1u) {
        val prefixWithKeyRange = packKey(tableDirs.tablePrefix, key.bytes)

        val iterator = FDBIterator(
            tr.getRange(FDBRange.startsWith(prefixWithKeyRange)).iterator()
        )

        checkExistence(iterator, prefixWithKeyRange)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.nonHistoricQualifierRetriever(prefixWithKeyRange)

        var index: Int
        changes = this.readStorageToChanges(
            getQualifier = getQualifier,
            select = select,
            creationVersion = if (creationVersion > fromVersion) creationVersion else null,
            processValue = { storageType, reference, valueWithVersionReader ->
                val currentVersion: ULong
                val keyBytes = iterator.current.key
                val valueBytes = iterator.current.value

                val value = when (storageType) {
                    ObjectDelete -> {
                        currentVersion = valueBytes.readVersionBytes()
                        cachedRead(reference, currentVersion) {
                            if (currentVersion >= fromVersion && keyBytes[prefixWithKeyRange.size] == 0.toByte()) {
                                valueBytes.last() == TRUE
                            } else null
                        }
                    }
                    Value -> {
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
                        currentVersion = valueBytes.readVersionBytes()
                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                index = VERSION_BYTE_SIZE
                                initIntByVar { valueBytes[index++] }
                            }
                        } else null
                    }
                    SetSize -> {
                        currentVersion = valueBytes.readVersionBytes()
                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                index = VERSION_BYTE_SIZE
                                initIntByVar { valueBytes[index++] }
                            }
                        } else null
                    }
                    MapSize -> {
                        currentVersion = valueBytes.readVersionBytes()
                        if (currentVersion >= fromVersion) {
                            cachedRead(reference, currentVersion) {
                                index = VERSION_BYTE_SIZE
                                initIntByVar { valueBytes[index++] }
                            }
                        } else null
                    }
                    Embed -> {
                        currentVersion = valueBytes.readVersionBytes()
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
        if (tableDirs !is HistoricTableDirectories) {
            throw RequestException("No historic table present so cannot use `toVersion` on get changes")
        }

        val prefixWithKeyRange = packKey(tableDirs.historicTablePrefix, key.bytes)
        val iterator = FDBIterator(
            tr.getRange(FDBRange.startsWith(prefixWithKeyRange)).iterator()
        )

        checkExistence(iterator, prefixWithKeyRange)

        var currentVersion: ULong = creationVersion
        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.historicQualifierRetriever(prefixWithKeyRange, toVersion ?: ULong.MAX_VALUE, maxVersions) { version ->
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
                                if (iterator.current.key[prefixWithKeyRange.size] == 0.toByte()) {
                                    val v = iterator.current.value
                                    v[0] == TRUE
                                } else null
                            }
                            Value -> {
                                val v = iterator.current.value
                                index = 0
                                val reader = { v[index++] }

                                val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                    ?: reference.propertyDefinition
                                readValue(definition, reader) {
                                    v.size - index
                                }
                            }
                            ListSize -> {
                                val v = iterator.current.value
                                index = 0
                                initIntByVar { v[index++] }
                            }
                            SetSize -> {
                                val v = iterator.current.value
                                index = 0
                                initIntByVar { v[index++] }
                            }
                            MapSize -> {
                                val v = iterator.current.value
                                index = 0
                                initIntByVar { v[index++] }
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

    if (changes.isEmpty()) return null
    return DataObjectVersionedChange(
        key = key,
        sortingKey = sortingKey?.let(::Bytes),
        changes = changes
    )
}
