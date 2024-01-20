package maryk.datastore.hbase.processors

import kotlinx.coroutines.future.await
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.definitions.index.IsIndexable
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.ScanUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.query.responses.updates.RemovalReason
import maryk.core.query.responses.updates.RemovalUpdate
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.setTimeRange
import maryk.datastore.hbase.softDeleteIndicator
import maryk.datastore.shared.Cache
import maryk.datastore.shared.ScanType.IndexScan
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Result

internal typealias ScanUpdatesStoreAction<DM> = StoreAction<DM, ScanUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyScanUpdatesStoreAction = ScanUpdatesStoreAction<IsRootDataModel>

/** Processes a ScanUpdatesRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processScanUpdatesRequest(
    storeAction: ScanUpdatesStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache
) {
    val scanRequest = storeAction.request
    val dbIndex = dataStore.getDataModelId(scanRequest.dataModel)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()

    var lastResponseVersion = 0uL

    var sortingKeys: MutableList<ByteArray>? = null
    var sortingIndex: IsIndexable? = null

    scanRequest.checkMaxVersions(dataStore.keepAllVersions)

    fun getSingleValues(key: Key<DM>, creationVersion: ULong, cacheReader: (IsPropertyReferenceForCache<*, *>, ULong, () -> Any?) -> Any?, result: Result): ValuesWithMetaData<DM>? {
        return scanRequest.dataModel.readResultIntoValuesWithMetaData(
            result,
            creationVersion,
            key,
            scanRequest.select,
            cacheReader
        )
    }

    val table = dataStore.getTable(scanRequest.dataModel)

    val actualGets = mutableListOf<Get>()
    val keys = mutableListOf<Key<DM>>()

    processScan(
        table,
        scanRequest,
        dataStore,
        scanSetup = {
            (it as? IndexScan)?.let { indexScan ->
                sortingKeys = mutableListOf()
                sortingIndex = indexScan.index
            }
        },
        scanLatestUpdate = true,
    ) { key, _, result, sortingKey ->
        matchingKeys.add(key)

        // Add sorting key
        sortingIndex?.let {
            sortingKey?.let {
                sortingKeys?.add(sortingKey)
            }
        }

        val lastVersion = result.getColumnLatestCell(dataColumnFamily, MetaColumns.LatestVersion.byteArray)?.timestamp?.toULong()
        if (lastVersion != null) {
            lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

            actualGets += Get(key.bytes).apply {
                addFamily(dataColumnFamily)
                readVersions(scanRequest.maxVersions.toInt())
                setTimeRange(scanRequest)
            }
            keys += key
        }
    }

    val results = table.getAll(actualGets).await()

    for ((index, result) in results.withIndex()) {
        if (result.isEmpty) {
            continue
        }

        val key = keys[index]
        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
            cache.readValue(dbIndex, key, reference, version, valueReader)
        }

        val createdVersion = result.getColumnLatestCell(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)?.timestamp?.toULong()

        scanRequest.dataModel.readResultIntoObjectChanges(
            result,
            createdVersion,
            key,
            scanRequest.select,
            sortingKeys?.getOrNull(index),
            cacheReader
        )?.let { objectChange ->
            updates += objectChange.changes.mapNotNull { versionedChange ->
                val changes = versionedChange.changes

                if (changes.contains(ObjectCreate)) {
                    val addedValues = scanRequest.dataModel.fromChanges(null, changes)

                    AdditionUpdate(
                        key = objectChange.key,
                        version = versionedChange.version,
                        firstVersion = versionedChange.version,
                        insertionIndex = matchingKeys.indexOf(objectChange.key),
                        isDeleted = false,
                        values = addedValues
                    )
                } else {
                    if (scanRequest.orderedKeys?.contains(objectChange.key) != false) {
                        ChangeUpdate(
                            key = objectChange.key,
                            version = versionedChange.version,
                            index = matchingKeys.indexOf(objectChange.key),
                            changes = changes
                        )
                    } else {
                        getSingleValues(key, createdVersion!!, cacheReader, result)?.let { valuesWithMeta ->
                            AdditionUpdate(
                                key = objectChange.key,
                                version = versionedChange.version,
                                firstVersion = valuesWithMeta.firstVersion,
                                insertionIndex = matchingKeys.indexOf(objectChange.key),
                                isDeleted = valuesWithMeta.isDeleted,
                                values = valuesWithMeta.values
                            )
                        }
                    }
                }
            }
        }
    }

    // Sort all updates on version, they are before sorted on data object order and then version
    updates.sortBy { it.version }

    lastResponseVersion = minOf(scanRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys,
            sortingKeys = sortingKeys?.map { Bytes(it) }
        )
    )

    scanRequest.orderedKeys?.let { orderedKeys ->
        // Remove values which should or should not be there from passed orderedKeys
        // This so the requester is up-to-date with any in between filtered values
        orderedKeys.subtract(matchingKeys.toSet()).let { removedKeys ->
            val deletedResults = table.getAll(removedKeys.map {
                Get(it.bytes).apply {
                    addColumn(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)
                    addColumn(dataColumnFamily, softDeleteIndicator)
                }
            }).await()

            for (deletedResult in deletedResults) {
                val isHardDelete = deletedResult.isEmpty
                val isSoftDelete = !isHardDelete && deletedResult.getColumnLatestCell(dataColumnFamily, softDeleteIndicator)?.valueArray?.get(0) == 1.toByte()

                updates += RemovalUpdate(
                    key = Key(deletedResult.row),
                    version = lastResponseVersion,
                    reason = when {
                        isHardDelete ->
                            RemovalReason.HardDelete
                        isSoftDelete ->
                            RemovalReason.SoftDelete
                        else -> RemovalReason.NotInRange
                    }
                )
            }
        }

        matchingKeys.subtract(orderedKeys.toSet()).let { addedKeys ->
            val addedResults = table.getAll(addedKeys.map {
                Get(it.bytes).apply {
                    addFamily(dataColumnFamily)
                }
            }).await()

            for (addedResult in addedResults) {
                // Only process it if it was created
                if (!addedResult.isEmpty) {
                    val createdVersion = addedResult.getColumnLatestCell(dataColumnFamily, MetaColumns.CreatedVersion.byteArray).timestamp.toULong()
                    val addedKey = Key<DM>(addedResult.row)

                    val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                        cache.readValue(dbIndex, addedKey, reference, version, valueReader)
                    }

                    getSingleValues(addedKey, createdVersion, cacheReader, addedResult)?.let { valuesWithMeta ->
                        updates += AdditionUpdate(
                            key = addedKey,
                            version = lastResponseVersion,
                            firstVersion = valuesWithMeta.firstVersion,
                            insertionIndex = matchingKeys.indexOf(addedKey),
                            isDeleted = valuesWithMeta.isDeleted,
                            values = valuesWithMeta.values
                        )
                    }
                }
            }
        }
    }

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = scanRequest.dataModel,
            updates = updates
        )
    )
}
