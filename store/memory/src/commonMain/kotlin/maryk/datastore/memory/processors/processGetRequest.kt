package maryk.datastore.memory.processors

import maryk.core.aggregations.Aggregator
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsSimpleValueDefinition
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.GetRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.memory.records.DataStore
import maryk.datastore.shared.RequestRecordCache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkToVersion

internal typealias GetStoreAction<DM> = StoreAction<DM, GetRequest<DM>, ValuesResponse<DM>>
internal typealias AnyGetStoreAction = GetStoreAction<IsRootDataModel>

/** Processes a GetRequest in a [storeAction] and resolve dataStore with [dataStoreFetcher] */
internal fun <DM : IsRootDataModel> processGetRequest(
    storeAction: GetStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>
) {
    val getRequest = storeAction.request
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(getRequest.keys.size.coerceAtLeast(4))
    val toVersion = getRequest.toVersion?.let(::HLC)

    val dataStore = dataStoreFetcher.invoke(getRequest.dataModel)

    getRequest.checkToVersion(dataStore.keepAllVersions)

    if (getRequest.keys.size > 1 && toVersion == null && getRequest.where == null && getRequest.aggregations == null &&
        (getRequest.select == null || getRequest.select?.properties?.any {
            getRequest.dataModel[it.index]?.definition !is IsSimpleValueDefinition<*, *>
        } == true)
    ) {
        readCurrentBatchGet(getRequest, dataStore, valuesWithMeta)
        storeAction.response.complete(ValuesResponse(getRequest.dataModel, valuesWithMeta, dataFetchType = FetchByKey))
        return
    }

    val aggregator = getRequest.aggregations?.let {
        Aggregator(it)
    }

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher, toVersion)
    var projectionPrepared = false
    var projectedQualifiers: List<ByteArray>? = null

    for (key in getRequest.keys) {
        val record = dataStore.getByKeyAtVersion(key, toVersion)
            ?: continue

        if (getRequest.shouldBeFiltered(record, toVersion, recordFetcher)) {
            continue
        }

        if (!projectionPrepared && getRequest.select != null && record.values.size >= 32) {
            projectedQualifiers = getRequest.dataModel.prepareMemoryProjectedQualifiers(getRequest.select)
            projectionPrepared = true
        }
        val valuesWithMetaData = getRequest.dataModel.recordToValueWithMeta(
            getRequest.select,
            toVersion,
            record,
            if (projectedQualifiers == null || record.values.size < 32) null else projectedQualifiers,
        )?.also {
            // Only add if not null
            valuesWithMeta.add(it)
        }

        aggregator?.aggregate {
            @Suppress("UNCHECKED_CAST")
            valuesWithMetaData?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                ?: record[it, toVersion]
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = getRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse(),
            dataFetchType = FetchByKey,
        )
    )
}

/** Mutations wait for this read action; each repeated result still owns its containers. */
private fun <DM : IsRootDataModel> readCurrentBatchGet(
    request: GetRequest<DM>,
    dataStore: DataStore<DM>,
    output: MutableList<ValuesWithMetaData<DM>>,
) {
    var prepared = false
    var records: RequestRecordCache<DM>? = null
    for (key in request.keys) {
        val record = dataStore.getByKeyAtVersion(key, null) ?: continue
        if (request.filterSoftDeleted && record.isDeleted(null)) continue
        val eligible = record.values.size in 32..16_384
        if (eligible && !prepared) {
            records = RequestRecordCache.create(request.keys, maxValueNodes = 16_384)
            prepared = true
        }
        val cache = records
        val value = if (eligible && cache != null) {
            if (cache.contains(key)) cache.get(key) else {
                val decoded = request.dataModel.recordToValueWithMeta(request.select, null, record, null)
                cache.put(key, decoded)
                decoded
            }
        } else request.dataModel.recordToValueWithMeta(request.select, null, record, null)
        if (value != null) output.add(value)
    }
}
