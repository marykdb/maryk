package maryk.datastore.foundationdb.processors

import kotlinx.coroutines.runBlocking
import maryk.core.aggregations.Aggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.definitions.IsStorageBytesEncodable
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction

internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootDataModel>

/** Processes a ScanRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanRequest(
    storeAction: ScanStoreAction<DM>,
    cache: Cache,
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = mutableListOf<ValuesWithMetaData<DM>>()
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val aggregator = scanRequest.aggregations?.let { Aggregator(it) }

    val responseFetchType = runTransaction { tr ->
        this@processScanRequest.processScan(
            scanRequest = scanRequest,
            tableDirs = tableDirs,
            scanSetup = { /* nothing */ }
        ) { key, creationVersion, _ ->
            val cacheReader = { ref: IsPropertyReferenceForCache<*, *>, version: ULong, reader: () -> Any? ->
                runBlocking { cache.readValue(dbIndex, key, ref, version, reader) }
            }

            val vwm = scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
                tr = tr,
                creationVersion = creationVersion,
                tableDirs = tableDirs,
                key = key,
                select = scanRequest.select,
                toVersion = scanRequest.toVersion,
                cachedRead = cacheReader
            )
            vwm?.also { valuesWithMeta.add(it) }

            aggregator?.aggregate {
                @Suppress("UNCHECKED_CAST")
                vwm?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                    ?: tr.getValue(
                        tableDirs = tableDirs,
                        toVersion = scanRequest.toVersion,
                        keyAndReference = it.toStorageByteArray()
                    ) { valueBytes, offset, length ->
                        (it.propertyDefinition as IsStorageBytesEncodable<Any>).fromStorageBytes(
                            valueBytes,
                            offset,
                            length
                        )
                    }
            }
        }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = scanRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse(),
            dataFetchType = responseFetchType,
        )
    )
}
