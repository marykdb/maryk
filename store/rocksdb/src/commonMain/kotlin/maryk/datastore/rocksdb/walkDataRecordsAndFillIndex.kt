package maryk.datastore.rocksdb

import maryk.core.extensions.bytes.toByteArray
import maryk.core.models.IsRootDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.graph
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.definitions.index.AnyOf
import maryk.core.properties.definitions.index.GeoHash
import maryk.core.properties.definitions.index.LegacyNormalize
import maryk.core.properties.definitions.index.Multiple
import maryk.core.properties.definitions.index.Normalize
import maryk.core.properties.definitions.index.ReferenceToMax
import maryk.core.properties.definitions.index.Reversed
import maryk.core.properties.definitions.index.Split
import maryk.core.properties.definitions.index.UUIDv4Key
import maryk.core.properties.definitions.index.UUIDv7Key
import maryk.core.properties.definitions.wrapper.EmbeddedValuesDefinitionWrapper
import maryk.core.properties.definitions.wrapper.IsDefinitionWrapper
import maryk.core.properties.graph.IsPropRefGraphNode
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.properties.graph.graph
import maryk.core.properties.references.AnyPropertyReference
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.datastore.rocksdb.processors.EMPTY_ARRAY
import maryk.datastore.rocksdb.processors.FALSE_ARRAY
import maryk.datastore.rocksdb.processors.SOFT_DELETE_INDICATOR
import maryk.datastore.rocksdb.processors.TRUE
import maryk.datastore.rocksdb.processors.StoreValuesGetter
import maryk.datastore.rocksdb.processors.isSoftDeleted
import maryk.datastore.rocksdb.processors.readTransactionIntoValuesWithMetaData
import maryk.datastore.rocksdb.processors.helpers.VERSION_BYTE_SIZE
import maryk.datastore.rocksdb.processors.helpers.createHistoricIndexKey
import maryk.datastore.rocksdb.processors.helpers.createIndexKey
import maryk.datastore.rocksdb.processors.helpers.readReversedVersionBytes
import maryk.datastore.rocksdb.processors.helpers.readVersionBytes
import maryk.datastore.rocksdb.processors.helpers.readVersionBytesIfExact
import maryk.datastore.rocksdb.processors.helpers.setIndexValue
import maryk.datastore.rocksdb.processors.helpers.toReversedVersionBytes
import maryk.lib.extensions.compare.matchesRangePart

/**
 * Walks all existing data records for [columnFamilies] of model in [dataStore]
 * Will index any [indexesToIndex] with relevant values
 */
internal fun walkDataRecordsAndFillIndex(
    dataStore: RocksDBDataStore,
    columnFamilies: TableColumnFamilies,
    indexesToIndex: List<IsIndexable>
) {
    Transaction(dataStore).use { transaction ->
        transaction.getIterator(dataStore.defaultReadOptions, columnFamilies.keys).use { iterator ->
            val storeGetter = StoreValuesGetter(
                null,
                dataStore.db,
                columnFamilies,
                dataStore.defaultReadOptions,
                captureVersion = true,
                decryptValue = { key, reference, value, offset, length ->
                    dataStore.decryptValueIfNeeded(columnFamilies.modelId, key, reference, value, offset, length)
                }
            )
            iterator.seekToFirst()
            while (iterator.isValid()) {
                val key = iterator.key()
                val creationVersion = iterator.value().readVersionBytesIfExact()
                if (creationVersion == null) {
                    iterator.next()
                    continue
                }

                storeGetter.moveToKey(key)
                if (columnFamilies !is HistoricTableColumnFamilies &&
                    !isSoftDeleted(transaction, columnFamilies, dataStore.defaultReadOptions, null, key)
                ) {
                    for (index in indexesToIndex) {
                        storeGetter.lastVersion = null
                        index.toStorageByteArraysForIndex(storeGetter, key).forEach { indexValue ->
                            setIndexValue(
                                transaction,
                                columnFamilies,
                                index.referenceStorageByteArray.bytes,
                                indexValue,
                                (storeGetter.lastVersion ?: creationVersion).toByteArray()
                            )
                        }
                    }
                }
                if (columnFamilies is HistoricTableColumnFamilies) {
                    rebuildHistoricalIndexes(dataStore, transaction, columnFamilies, key, creationVersion, indexesToIndex, storeGetter)
                }
                iterator.next()
            }
            transaction.commit()
        }
    }
}

/** Diff complete states so fan-out entries and deletion boundaries remain independent. */
private fun rebuildHistoricalIndexes(
    dataStore: RocksDBDataStore,
    transaction: Transaction,
    columnFamilies: HistoricTableColumnFamilies,
    keyBytes: ByteArray,
    creationVersion: ULong,
    indexes: List<IsIndexable>,
    currentValues: StoreValuesGetter,
) {
    val versions = mutableSetOf(creationVersion)
    val latestDeleteMarker = transaction.get(
        columnFamilies.table, dataStore.defaultReadOptions, keyBytes + SOFT_DELETE_INDICATOR,
    )?.takeIf { it.size == VERSION_BYTE_SIZE + 1 }
    val latestDeleteVersion = latestDeleteMarker?.readVersionBytes()
    latestDeleteVersion?.takeIf { it >= creationVersion }?.let { versions += it }
    transaction.getIterator(dataStore.sequentialReadOptions, columnFamilies.historic.table).use { iterator ->
        iterator.seek(keyBytes)
        while (iterator.isValid()) {
            val storedKey = iterator.key()
            if (!storedKey.matchesRangePart(0, keyBytes)) break
            if (storedKey.size > keyBytes.size + VERSION_BYTE_SIZE) {
                val version = storedKey.readReversedVersionBytes(storedKey.size - VERSION_BYTE_SIZE)
                if (version >= creationVersion) versions += version
            }
            iterator.next()
        }
    }
    val model = dataStore.dataModelsById.getValue(columnFamilies.modelId)
    val key = Key<IsRootDataModel>(keyBytes)
    val select = model.indexSelection(indexes)
    var previous = List(indexes.size) { emptyMap<Bytes, ByteArray>() }
    val activeVersions = List(indexes.size) { mutableMapOf<Bytes, ULong>() }
    transaction.getIterator(dataStore.sequentialReadOptions, columnFamilies.historic.table).use { iterator ->
        for (version in versions.sorted()) {
            val snapshot = model.readTransactionIntoValuesWithMetaData(
                iterator, dataStore, creationVersion, columnFamilies, key, select, version,
            ) { _, _, read -> read() }
            val isDeleted = if (latestDeleteVersion != null && latestDeleteVersion <= version) {
                latestDeleteMarker[VERSION_BYTE_SIZE] == TRUE
            } else snapshot?.isDeleted == true
            val current = indexes.map { index ->
                if (snapshot == null || isDeleted) emptyMap()
                else index.toStorageByteArraysForIndex(snapshot.values, keyBytes).associateBy { Bytes(it) }
            }
            for ((position, index) in indexes.withIndex()) {
                val reference = index.referenceStorageByteArray.bytes
                val reversedVersion = version.toReversedVersionBytes()
                for ((identity, value) in previous[position]) {
                    if (identity !in current[position]) {
                        activeVersions[position].remove(identity)
                        transaction.put(columnFamilies.historic.index, createHistoricIndexKey(reference, value, reversedVersion), FALSE_ARRAY)
                    }
                }
                for ((identity, value) in current[position]) {
                    if (identity !in previous[position]) {
                        activeVersions[position][identity] = version
                        transaction.put(columnFamilies.historic.index, createHistoricIndexKey(reference, value, reversedVersion), EMPTY_ARRAY)
                    }
                }
            }
            previous = current
        }
    }
    // Valid history cannot make an unreadable current cell safe to publish in the current index.
    if (isSoftDeleted(transaction, columnFamilies, dataStore.defaultReadOptions, null, keyBytes)) return
    for ((position, index) in indexes.withIndex()) {
        currentValues.lastVersion = null
        for (value in index.toStorageByteArraysForIndex(currentValues, keyBytes)) {
            val version = activeVersions[position][Bytes(value)] ?: currentValues.lastVersion ?: creationVersion
            transaction.put(
                columnFamilies.index,
                createIndexKey(index.referenceStorageByteArray.bytes, value),
                version.toByteArray(),
            )
        }
    }
}

/** Built-in indexes declare their dependencies through references and transforms. */
private fun IsIndexable.collectIndexReferences(references: MutableList<AnyPropertyReference>): Boolean = when (this) {
    is Multiple -> this.references.all { it.collectIndexReferences(references) }
    is AnyOf -> this.references.all { it.collectIndexReferences(references) }
    is Normalize -> reference.collectIndexReferences(references)
    is LegacyNormalize -> reference.collectIndexReferences(references)
    is Split -> reference.collectIndexReferences(references)
    is GeoHash -> reference.collectIndexReferences(references)
    is Reversed<*> -> reference.collectIndexReferences(references)
    is ReferenceToMax<*> -> reference.collectIndexReferences(references)
    is AnyPropertyReference -> { references += this; true }
    UUIDv4Key, UUIDv7Key -> true
    else -> false
}

private fun IsRootDataModel.indexSelection(indexes: List<IsIndexable>): RootPropRefGraph<IsRootDataModel>? {
    val references = mutableListOf<AnyPropertyReference>()
    // Custom indexes have no dependency contract, so retain the conservative full selection.
    if (!indexes.all { it.collectIndexReferences(references) }) return null
    return graph { indexSelectionNodes(references.map { it.unwrap() }) }
}

@Suppress("UNCHECKED_CAST")
private fun <DM : IsValuesDataModel> DM.indexSelectionNodes(
    paths: List<List<AnyPropertyReference>>,
): List<IsPropRefGraphNode<DM>> = paths.groupBy {
    (it.first().propertyDefinition as IsDefinitionWrapper<*, *, *, *>).index
}.entries.sortedBy { it.key }.map { (index, branches) ->
    val wrapper = requireNotNull(this[index])
    if (wrapper is EmbeddedValuesDefinitionWrapper<*, *> && branches.all { it.size > 1 }) {
        graph(wrapper as EmbeddedValuesDefinitionWrapper<IsValuesDataModel, IsPropertyContext>) {
            indexSelectionNodes(branches.map { it.drop(1) })
        }
    } else {
        // Collection and typed references need their enclosing value to resolve index components.
        wrapper
    }
}
