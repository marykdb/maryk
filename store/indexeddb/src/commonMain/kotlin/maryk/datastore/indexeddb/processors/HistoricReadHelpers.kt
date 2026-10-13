package maryk.datastore.indexeddb.processors

import maryk.core.exceptions.RequestException
import maryk.core.exceptions.StorageException
import maryk.core.models.IsRootDataModel
import maryk.core.models.emptyValues
import maryk.core.models.key
import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.Change
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.changes.VersionedChanges
import maryk.core.query.changes.change
import maryk.core.query.requests.change
import maryk.datastore.indexeddb.IndexedDbByteStore
import maryk.datastore.indexeddb.createObjectRowKeyPrefix
import maryk.datastore.indexeddb.decryptSelectedStorageRows
import maryk.datastore.indexeddb.decodeStorageRowsToValues
import maryk.datastore.indexeddb.scanInBatches
import maryk.datastore.indexeddb.decodeRecordMeta
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
    currentRecord: ValuesWithMetaData<DM>? = null,
    keyBytes: ByteArray,
    fromVersion: ULong,
    toVersion: ULong?,
    maxVersions: UInt,
    select: RootPropRefGraph<DM>?,
    decryptValue: suspend (ByteArray, ByteArray) -> ByteArray = { _, value -> value },
    decryptChangePayload: suspend (ULong, ByteArray) -> ByteArray,
): List<VersionedChanges> {
    val creationChanges = mutableListOf<VersionedChanges>()
    val nonCreationChanges = mutableListOf<VersionedChanges>()

    val rows = scanObjectScopedRows(changeStoreName, keyBytes)

    for ((rowKey, rowValue) in rows) {
        val version = rowKey.readTrailingVersion()
        if (version < fromVersion || (toVersion != null && version > toVersion)) continue
        if (rowValue.isUnserializableChangeLogMarker()) {
            val historicRecord = historicTableStoreName?.let {
                readHistoricRecord(dataModel, it, keyBytes, version, select, decryptValue)
            }
            val reconstructableRecord = historicRecord?.takeIf {
                it.firstVersion == version && it.lastVersion == version
            }
                ?: currentRecord?.takeIf { it.firstVersion == version && it.lastVersion == version }
                ?: throw RequestException(
                    "Cannot reconstruct IndexedDB change-log marker for ${dataModel.Meta.name} " +
                        "at version $version without an exact stored snapshot"
                )
            creationChanges += reconstructableRecord.toCreationChanges(fromVersion, toVersion, select)
            continue
        }

        val decoded = decodeStoredVersionedChange(dataModel, decryptChangePayload(version, rowValue), keyBytes, version)
        for (versionedChanges in decoded.changes) {
            if (versionedChanges.version < fromVersion) continue
            if (toVersion != null && versionedChanges.version > toVersion) continue

            val filteredChanges = versionedChanges.changes.mapNotNull { change ->
                if (select == null) change else change.filterWithSelect(select)
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

    val laterChangedReferences = limitedNonCreationChanges.flatMapTo(mutableSetOf()) { versionedChanges ->
        versionedChanges.changes.filterIsInstance<Change>().flatMap { it.referenceValuePairs.map { pair -> pair.reference } }
    }
    val returnedCreationChanges = if (limitedNonCreationChanges.isEmpty() || laterChangedReferences.isEmpty()) {
        creationChanges
    } else {
        creationChanges.map { versionedChanges ->
            versionedChanges.copy(
                changes = versionedChanges.changes.mapNotNull { change ->
                    when (change) {
                        is Change -> change.referenceValuePairs
                            .filterNot { it.reference in laterChangedReferences }
                            .takeIf { it.isNotEmpty() }
                            ?.let { Change(*it.toTypedArray()) }
                        else -> change
                    }
                }
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
    decryptValue: suspend (ByteArray, ByteArray) -> ByteArray = { _, value -> value },
): ValuesWithMetaData<DM>? {
    val value = readHistoricSnapshotBytes(storeName, keyBytes, toVersion) ?: return null
    val (meta, storageRows) = decodeHistoricSnapshot(value)
    val values = decodeStorageRowsToValues(
        dataModel,
        decryptSelectedStorageRows(dataModel, storageRows, select, decryptValue),
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

/** Resolve a canonical snapshot before any logical read or filter limit. */
internal suspend fun IndexedDbByteStore.readHistoricSnapshotBytes(
    storeName: String,
    keyBytes: ByteArray,
    toVersion: ULong,
): ByteArray? {
    val rowKeyPrefix = createObjectRowKeyPrefix(keyBytes)
    fun isSnapshotIdentity(rowKey: ByteArray) =
        rowKey.size == rowKeyPrefix.size + ULong.SIZE_BYTES &&
            rowKey.matchesRangePart(0, rowKeyPrefix, sourceLength = rowKey.size, length = rowKeyPrefix.size)

    fun checkedSnapshot(rowKey: ByteArray, rowValue: ByteArray): ByteArray {
        if (rowValue.size < 17) throw StorageException("Invalid IndexedDB historic snapshot metadata")
        val meta = decodeRecordMeta(rowValue.copyOfRange(0, 17))
        if (meta.lastVersion != rowKey.readTrailingInvertedVersion() || meta.firstVersion > meta.lastVersion) {
            throw StorageException("IndexedDB historic snapshot metadata does not match its stored revision")
        }
        return rowValue
    }

    // Keep ordinary point reads at one snapshot; batch only after an invalid identity.
    val first = scan(
        storeName = storeName,
        startKey = createHistoricSnapshotRowKey(keyBytes, toVersion),
        endKey = keyPrefixUpperBound(rowKeyPrefix),
        includeEnd = false,
        limit = 1u,
    ).firstOrNull() ?: return null
    if (isSnapshotIdentity(first.first)) return checkedSnapshot(first.first, first.second)

    var snapshot: ByteArray? = null
    scanInBatches(
        storeName = storeName,
        startKey = first.first,
        includeStart = false,
        endKey = keyPrefixUpperBound(rowKeyPrefix),
        includeEnd = false,
        targetLimit = UInt.MAX_VALUE,
    ) { rowKey, rowValue ->
        if (isSnapshotIdentity(rowKey)) {
            snapshot = checkedSnapshot(rowKey, rowValue)
            false
        } else {
            true
        }
    }

    return snapshot
}

internal suspend fun IndexedDbByteStore.readHistoricUniqueKey(
    storeName: String,
    uniqueKey: ByteArray,
    toVersion: ULong,
): ByteArray? {
    var resolvedKey: ByteArray? = null
    scanInBatches(
        storeName = storeName,
        startKey = createHistoricVersionedRowKey(uniqueKey, toVersion),
        endKey = keyPrefixUpperBound(uniqueKey),
        includeEnd = false,
        targetLimit = UInt.MAX_VALUE,
    ) { rowKey, rowValue ->
        if (
            rowKey.size == uniqueKey.size + ULong.SIZE_BYTES &&
            rowKey.matchesRangePart(0, uniqueKey, sourceLength = rowKey.size, length = uniqueKey.size)
        ) {
            resolvedKey = rowValue.takeUnless { it.isEmpty() }
            false
        } else {
            true
        }
    }
    return resolvedKey
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
