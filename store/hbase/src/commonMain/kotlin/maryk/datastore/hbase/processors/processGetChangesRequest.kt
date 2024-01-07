package maryk.datastore.hbase.processors

import kotlinx.coroutines.future.await
import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.GetChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.metaColumnFamily
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion
import org.apache.hadoop.hbase.client.Get

internal typealias GetChangesStoreAction<DM> = StoreAction<DM, GetChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyGetChangesStoreAction = GetChangesStoreAction<IsRootDataModel>

/** Processes a GetChangesRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processGetChangesRequest(
    storeAction: GetChangesStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache
) {
    val getRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    getRequest.checkToVersion(dataStore.keepAllVersions)
    getRequest.checkMaxVersions(dataStore.keepAllVersions)

    val gets = getRequest.keys.map {
        Get(it.bytes).apply {
            addFamily(metaColumnFamily)
            addFamily(dataColumnFamily)
            setFilter(getRequest.createFilter())
            readVersions(getRequest.maxVersions.toInt())
            setTimeRange(getRequest.fromVersion.toLong(), getRequest.toVersion?.toLong() ?: Long.MAX_VALUE)
        }
    }

    val table = dataStore.getTable(getRequest.dataModel)
    val dbIndex = dataStore.getDataModelId(getRequest.dataModel)

    val results = table.getAll(gets).await()

    keyWalk@for (result in results) {
        if (result.isEmpty) {
            continue@keyWalk
        }
        val key = Key<DM>(result.row)

        val creationVersion = result.getColumnLatestCell(metaColumnFamily, MetaColumns.CreatedVersion.byteArray).timestamp.toULong()

        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
            cache.readValue(dbIndex, key, reference, version, valueReader)
        }

        getRequest.dataModel.readResultIntoObjectChanges(
            result = result,
            creationVersion = creationVersion,
            key = key,
            select = getRequest.select,
            fromVersion = getRequest.fromVersion,
            sortingKey = null,
            cachedRead = cacheReader
        )?.also {
            // Only add if not null
            objectChanges.add(it)
        }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = getRequest.dataModel,
            changes = objectChanges
        )
    )
}
