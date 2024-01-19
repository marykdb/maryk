package maryk.datastore.hbase.processors

import kotlinx.coroutines.future.await
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.helpers.setTimeRange
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion
import org.apache.hadoop.hbase.client.Get
import org.apache.hadoop.hbase.client.Result

internal typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyGetUpdatesStoreAction = GetUpdatesStoreAction<IsRootDataModel>

/** Processes a GetUpdatesRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache
) {
    val getRequest = storeAction.request

    getRequest.checkToVersion(dataStore.keepAllVersions)
    getRequest.checkMaxVersions(dataStore.keepAllVersions)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    var lastResponseVersion = 0uL
    var insertionIndex = -1

    val table = dataStore.getTable(getRequest.dataModel)

    val dbIndex = dataStore.getDataModelId(getRequest.dataModel)

    // First get latest versions for all requested keys
    val getLatestVersions = getRequest.keys.map {
        Get(it.bytes).apply {
            addFamily(dataColumnFamily)
            setFilter(getRequest.createFilter())

            // For the ordered key I need the last version, so I have to start at the first version
            if (getRequest.toVersion != null) {
                setTimeRange(0, getRequest.toVersion!!.toLong() + 1)
            }

            addColumn(dataColumnFamily, MetaColumns.LatestVersion.byteArray)
        }
    }

    val resultsLatestVersions = table.getAll(getLatestVersions).await()

    // Now read all actual changes, don't need to filter anymore.
    val gets = resultsLatestVersions.mapNotNull { result ->
        if (result.isEmpty) {
            null
        } else {
            val lastVersion = result.getColumnLatestCell(dataColumnFamily, MetaColumns.LatestVersion.byteArray).timestamp.toULong()

            val key = Key<DM>(result.row)
            matchingKeys.add(key)
            lastResponseVersion = maxOf(lastResponseVersion, lastVersion)

            if (lastVersion < getRequest.fromVersion) {
                null
            } else {
                Get(result.row).apply {
                    addFamily(dataColumnFamily)

                    readVersions(getRequest.maxVersions.toInt())

                    setTimeRange(getRequest)
                }
            }
        }
    }

    val results = table.getAll(gets).await()

    keyWalk@ for (result in results) {
        if (result.isEmpty) {
            continue@keyWalk
        }
        val key = Key<DM>(result.row)

        val createdVersion =
            result.getColumnLatestCell(dataColumnFamily, MetaColumns.CreatedVersion.byteArray)?.timestamp?.toULong()

        insertionIndex++

        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
            cache.readValue(dbIndex, key, reference, version, valueReader)
        }

        val filteredResultOnStartVersion = Result.create(
            result.rawCells().filter {
                it.timestamp.toULong() >= getRequest.fromVersion
            },
            result.exists,
            result.isStale,
            result.mayHaveMoreCellsInRow(),
        )

        if (filteredResultOnStartVersion.isEmpty) {
            continue@keyWalk
        }

        getRequest.dataModel.readResultIntoObjectChanges(
            filteredResultOnStartVersion,
            createdVersion,
            key,
            getRequest.select,
            null,
            cacheReader
        )?.also { objectChange ->
            updates += objectChange.changes.map { versionedChange ->
                val changes = versionedChange.changes

                if (changes.contains(ObjectCreate)) {
                    val addedValues = getRequest.dataModel.fromChanges(null, changes)

                    AdditionUpdate(
                        objectChange.key,
                        versionedChange.version,
                        versionedChange.version,
                        insertionIndex,
                        false,
                        addedValues
                    )
                } else {
                    ChangeUpdate(
                        objectChange.key,
                        versionedChange.version,
                        insertionIndex,
                        changes
                    )
                }
            }
        }
    }

    // Sort all updates on version, they are before sorted on data object order and then version
    updates.sortBy { it.version }

    lastResponseVersion = minOf(getRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(
        0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys
        )
    )

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = getRequest.dataModel,
            updates = updates
        )
    )
}
