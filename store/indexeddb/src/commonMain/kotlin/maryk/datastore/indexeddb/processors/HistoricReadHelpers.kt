package maryk.datastore.indexeddb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.models.emptyValues
import maryk.core.models.key
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.datastore.indexeddb.IndexedDbByteStore
import maryk.datastore.indexeddb.createObjectRowKeyPrefix
import maryk.datastore.indexeddb.decodeStorageRowsToValues
import maryk.datastore.indexeddb.scanInBatches
import maryk.lib.extensions.compare.compareTo
import maryk.lib.extensions.compare.matchesRangePart



internal suspend fun IndexedDbByteStore.scanObjectScopedRows(
    storeName: String,
    keyBytes: ByteArray,
): List<Pair<ByteArray, ByteArray>> {
    val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
    return scan(
        storeName = storeName,
        startKey = rowKeyPrefix,
        endKey = keyPrefixUpperBound(rowKeyPrefix),
        includeEnd = false,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
    }
}

internal suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readChangeLog(
    dataModel: DM,
    changeStoreName: String,
    historicTableStoreName: String?,
    keyBytes: ByteArray,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): List<VersionedChanges> {
    val creationChanges = mutableListOf<VersionedChanges>()
    val nonCreationChanges = mutableListOf<VersionedChanges>()

    val rows = scanObjectScopedRows(changeStoreName, keyBytes)

    for ((rowKey, rowValue) in rows) {
        if (rowValue.isUnserializableChangeLogMarker()) {
            val version = rowKey.readTrailingVersion()
            val historicRecord = historicTableStoreName?.let {
                readHistoricRecord(dataModel, it, keyBytes, version, select, decryptValue)
            }
            if (historicRecord?.firstVersion == version) {
                creationChanges += historicRecord.toCreationChanges(fromVersion, toVersion, select)
            }
            continue
        }

        val decoded = decodeVersionedChange(dataModel, rowValue)
        for (versionedChanges in decoded.changes) {
            if (versionedChanges.version < fromVersion) continue
            if (toVersion != null && versionedChanges.version > toVersion) continue

            val filteredChanges = versionedChanges.changes.mapNotNull { change ->
                select?.let { change.filterWithSelect(it) } ?: change
            }
            if (filteredChanges.isEmpty()) continue

            val filtered = versionedChanges.copy(changes = filteredChanges)
            if (filteredChanges.any { it is ObjectCreate }) {
                creationChanges += filtered
            } else {
                nonCreationChanges += filtered
            }
        }
    }

    val limitedNonCreationChanges = if (maxVersions >= nonCreationChanges.size.toUInt()) {
        nonCreationChanges
    } else {
        nonCreationChanges.takeLast(maxVersions.toInt())
    }

    val returnedCreationChanges = if (limitedNonCreationChanges.isEmpty()) {
        creationChanges
    } else {
        creationChanges.map { versionedChanges ->
            versionedChanges.copy(
                changes = versionedChanges.changes.filterIsInstance<ObjectCreate>()
            )
        }.filter { it.changes.isNotEmpty() }
    }

    return returnedCreationChanges + limitedNonCreationChanges
}

internal suspend fun IndexedDbByteStore.historicCleanupRowsForKey(
    storeName: String,
    keyBytes: ByteArray,
): List<Pair<ByteArray, ByteArray>> =
    scanObjectScopedRows(storeName, keyBytes)

internal suspend fun <DM : IsRootDataModel> IndexedDbByteStore.readHistoricRecord(
    dataModel: DM,
    storeName: String,
    keyBytes: ByteArray,
    toVersion: ULong,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray) -> ByteArray = { it },
): ValuesWithMetaData<DM>? {
    val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
    val rows = scan(
        storeName = storeName,
        startKey = createHistoricSnapshotRowKey(keyBytes, toVersion),
        endKey = keyPrefixUpperBound(rowKeyPrefix),
        includeEnd = false,
        limit = 1u,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)
    }

    val (_, value) = rows.firstOrNull() ?: return null
    val (meta, storageRows) = decodeHistoricSnapshot(value)
    val values = decodeStorageRowsToValues(
        dataModel,
        storageRows.map { (qualifier, rowValue) -> qualifier to decryptValue(rowValue) },
        select,
    )
        ?: dataModel.emptyValues()

    return ValuesWithMetaData(
        key = dataModel.key(keyBytes),
        values = values,
        firstVersion = meta.firstVersion,
        lastVersion = meta.lastVersion,
        isDeleted = meta.isDeleted,
    )
}

internal suspend fun IndexedDbByteStore.readHistoricUniqueKey(
    storeName: String,
    uniqueKey: ByteArray,
    toVersion: ULong,
): ByteArray? {
    val rows = scan(
        storeName = storeName,
        startKey = createHistoricVersionedRowKey(uniqueKey, toVersion),
        endKey = keyPrefixUpperBound(uniqueKey),
        includeEnd = false,
        limit = 1u,
    ).filter { (rowKey, _) ->
        rowKey.matchesRangePart(0, uniqueKey, sourceLength = rowKey.size, length = uniqueKey.size)
    }

    return rows.firstOrNull()?.second?.takeUnless { it.isEmpty() }
}

internal suspend fun IndexedDbByteStore.readHistoricIndexRows(
    storeName: String,
    startKey: ByteArray,
    endKey: ByteArray,
    includeEnd: Boolean,
    toVersion: ULong,
    reverse: Boolean,
): List<Pair<ByteArray, ByteArray>> {
    val latestByBaseKey = mutableMapOf<String, Triple<ByteArray, ULong, ByteArray>>()

    scanInBatches(
        storeName = storeName,
        startKey = startKey,
        endKey = endKey,
        includeEnd = includeEnd,
        targetLimit = UInt.MAX_VALUE,
    ) { rowKey, rowValue ->
        if (rowKey.size < ULong.SIZE_BYTES) return@scanInBatches true
        val baseKey = rowKey.copyOfRange(0, rowKey.size - ULong.SIZE_BYTES)
        val version = rowKey.readTrailingInvertedVersion()
        if (version > toVersion) return@scanInBatches true

        val mapKey = baseKey.contentToString()
        val current = latestByBaseKey[mapKey]
        if (current == null || version > current.second) {
            latestByBaseKey[mapKey] = Triple(baseKey, version, rowValue)
        }
        true
    }

    val activeRows = latestByBaseKey.values
        .filter { (_, _, value) -> value.firstOrNull() == 1.toByte() }
        .map { (baseKey, _, value) -> baseKey to value }
        .sortedWith { a, b -> a.first compareTo b.first }

    return if (reverse) activeRows.asReversed() else activeRows
}
