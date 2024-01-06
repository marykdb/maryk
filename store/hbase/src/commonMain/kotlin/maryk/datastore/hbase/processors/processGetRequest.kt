package maryk.datastore.hbase.processors

import kotlinx.coroutines.future.await
import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.hbase.HbaseDataStore
import maryk.datastore.hbase.MetaColumns
import maryk.datastore.hbase.dataColumnFamily
import maryk.datastore.hbase.metaColumnFamily
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion
import org.apache.hadoop.hbase.client.Get

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

/** Processes a GetRequest in a [storeAction] into a [dataStore] */
internal suspend fun <DM : IsRootDataModel> processGetRequest(
    storeAction: GetStoreAction<DM>,
    dataStore: HbaseDataStore,
    cache: Cache
) {
    val getRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM>>()
    val dbIndex = dataStore.getDataModelId(getRequest.dataModel)

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    getRequest.checkToVersion(dataStore.keepAllVersions)

    val table = dataStore.getTable(getRequest.dataModel)

    val gets = getRequest.keys.map {
        Get(it.bytes).apply {
            addFamily(metaColumnFamily)
            addFamily(dataColumnFamily)
            readVersions(1)
            getRequest.toVersion?.let { toVersion ->
                setTimeRange(0, toVersion.toLong())
            }
        }
    }

    val results = table.getAll(gets).await()

    keyWalk@for (result in results) {
        if (result.isEmpty) {
            continue@keyWalk
        }
        val key = Key<DM>(result.row)
        val creationVersion = result.getColumnLatestCell(metaColumnFamily, MetaColumns.CreatedVersion.byteArray).timestamp.toULong()

        if (
            getRequest.shouldBeFiltered(result, result.row, 0, result.row.size, creationVersion, getRequest.toVersion)
        ) {
            continue@keyWalk
        }

        val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
            cache.readValue(dbIndex, key, reference, version, valueReader)
        }

        val valuesWithMetaData = getRequest.dataModel.readResultIntoValuesWithMetaData(
            result,
            creationVersion,
            key,
            getRequest.select,
            cacheReader
        )?.also {
            // Only add if not null
            valuesWithMeta.add(it)
        }

        aggregator?.aggregate {
            @Suppress("UNCHECKED_CAST")
            valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                ?: result.getColumnLatestCell(dataColumnFamily, it.toStorageByteArray()).let { valueCell ->
                    (it.propertyDefinition as IsStorageBytesEncodable<Any>).fromStorageBytes(valueCell.valueArray, valueCell.valueOffset, valueCell.valueLength)
                }
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse()
        )
    )
}
