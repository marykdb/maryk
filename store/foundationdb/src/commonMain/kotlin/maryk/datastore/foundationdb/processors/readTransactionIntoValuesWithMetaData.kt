package maryk.datastore.foundationdb.processors

import com.apple.foundationdb.Transaction
import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.extensions.bytes.initIntByVar
import maryk.core.models.IsRootDataModel
import maryk.core.models.values
import maryk.core.processors.datastore.StorageTypeEnum.Embed
import maryk.core.processors.datastore.StorageTypeEnum.ListSize
import maryk.core.processors.datastore.StorageTypeEnum.MapSize
import maryk.core.processors.datastore.StorageTypeEnum.ObjectDelete
import maryk.core.processors.datastore.StorageTypeEnum.SetSize
import maryk.core.processors.datastore.StorageTypeEnum.TypeValue
import maryk.core.processors.datastore.StorageTypeEnum.Value
import maryk.core.processors.datastore.readStorageToValues
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.EmptyValueItems
import maryk.core.values.Values
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
import com.apple.foundationdb.Range as FDBRange

/**
 * Read values for [key] from a FoundationDB [tr] into a ValuesWithMeta object.
 * Filters results on [select] and uses [toVersion] if provided.
 *
 * - Build a getQualifier lambda that advances an underlying iterator
 * - Expose the current KV through captured state for processValue
 * - Use AsyncIterable directly; no custom walker class
 */
internal fun <DM : IsRootDataModel> DM.readTransactionIntoValuesWithMetaData(
    tr: Transaction,
    creationVersion: ULong,
    tableDirs: IsTableDirectories,
    key: Key<DM>,
    select: RootPropRefGraph<DM>?,
    toVersion: ULong?,
    cachedRead: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?
): ValuesWithMetaData<DM>? {
    var maxVersion = creationVersion
    var isDeleted = false

    val values: Values<DM> = if (select != null && select.properties.isEmpty()) {
        // Don't read the values if no values are selected
        this.values(null) { EmptyValueItems }
    } else if (toVersion == null) {
        val prefixWithKeyRange = packKey(tableDirs.tablePrefix, key.bytes)

        val iterator = FDBIterator(
            tr.getRange(FDBRange.startsWith(prefixWithKeyRange)).iterator()
        )

        checkExistence(iterator, prefixWithKeyRange)

        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.nonHistoricQualifierRetriever(prefixWithKeyRange)

        var index: Int
        this.readStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, reference ->
                val currentVersion: ULong
                val value = iterator.current.value

                when (storageType) {
                    ObjectDelete -> {
                        currentVersion = maxOf(value.readVersionBytes(), maxVersion)
                        index = VERSION_BYTE_SIZE
                        isDeleted = value[index] == TRUE
                        true
                    }
                    Value -> {
                        currentVersion = value.readVersionBytes()

                        index = VERSION_BYTE_SIZE
                        val reader = { value[index++] }

                        cachedRead(reference, currentVersion) {
                            val definition = (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                ?: reference.propertyDefinition

                            readValue(definition, reader) {
                                value.size - index
                            }
                        }
                    }
                    ListSize -> {
                        currentVersion = value.readVersionBytes()
                        index = VERSION_BYTE_SIZE

                        cachedRead(reference, currentVersion) {
                            initIntByVar { value[index++] }
                        }
                    }
                    SetSize -> {
                        currentVersion = value.readVersionBytes()
                        index = VERSION_BYTE_SIZE

                        cachedRead(reference, currentVersion) {
                            initIntByVar { value[index++] }
                        }
                    }
                    MapSize -> {
                        currentVersion = value.readVersionBytes()

                        index = VERSION_BYTE_SIZE
                        cachedRead(reference, currentVersion) {
                            initIntByVar { value[index++] }
                        }
                    }
                    Embed -> {
                        currentVersion = value.readVersionBytes()
                    }
                    TypeValue -> throw StorageException("Not used in direct encoding")
                }.also {
                    maxVersion = maxOf(currentVersion, maxVersion)
                }
            }
        )
    } else {
        if (tableDirs !is HistoricTableDirectories) {
            throw RequestException("No historic table present so cannot use `toVersion` on get")
        }

        val prefixWithKeyRange = packKey(tableDirs.historicTablePrefix, key.bytes)

        val iterator = FDBIterator(
            tr.getRange(FDBRange.startsWith(prefixWithKeyRange)).iterator()
        )

        checkExistence(iterator, prefixWithKeyRange)

        var currentVersion = 0uL
        // Will start by going to next key so will miss the creation timestamp
        val getQualifier = iterator.historicQualifierRetriever(prefixWithKeyRange, toVersion, 1u) { version ->
            currentVersion = version
            maxVersion = maxOf(currentVersion, maxVersion)
        }

        var index: Int
        this.readStorageToValues(
            getQualifier = getQualifier,
            select = select,
            processValue = { storageType, reference ->
                cachedRead(reference, currentVersion) {
                    val key = iterator.current.key
                    val value = iterator.current.value
                    when (storageType) {
                        ObjectDelete -> {
                            if (key[prefixWithKeyRange.size] == SOFT_DELETE_INDICATOR) {
                                val value = value
                                isDeleted = value[0] == TRUE
                                isDeleted
                            } else null
                        }
                        Value -> {
                            index = 0
                            val reader = { value[index++] }

                            val definition =
                                (reference.propertyDefinition as? IsDefinitionWrapper<*, *, *, *>)?.definition
                                    ?: reference.propertyDefinition
                            readValue(definition, reader) {
                                value.size - index
                            }
                        }
                        ListSize -> {
                            index = 0
                            initIntByVar { value[index++] }
                        }
                        SetSize -> {
                            index = 0
                            initIntByVar { value[index++] }
                        }
                        MapSize -> {
                            index = 0
                            initIntByVar { value[index++] }
                        }
                        Embed -> {}
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
