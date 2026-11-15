package maryk.datastore.foundationdb.processors

import maryk.foundationdb.Transaction
import maryk.datastore.foundationdb.processors.helpers.cachedValueReader
import maryk.datastore.foundationdb.processors.helpers.PreparedRecordRead
import maryk.datastore.foundationdb.processors.helpers.prepareRecordRead
import maryk.datastore.shared.createRecordAggregator
import maryk.core.models.IsRootDataModel
import maryk.core.properties.definitions.IsPropertyDefinition
import maryk.core.properties.references.IsPropertyReference
import maryk.core.properties.types.Key
import maryk.core.query.ValuesWithMetaData
import maryk.core.query.requests.ScanRequest
import maryk.core.query.requests.createCursor
import maryk.core.query.responses.ValuesResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.FoundationDBReadContext
import maryk.datastore.foundationdb.processors.helpers.getValue
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.helpers.convertToValue

internal typealias ScanStoreAction<DM> = StoreAction<DM, ScanRequest<DM>, ValuesResponse<DM>>
internal typealias AnyScanStoreAction = ScanStoreAction<IsRootDataModel>

/** Processes a ScanRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanRequest(
    storeAction: ScanStoreAction<DM>,
    cache: Cache,
    readContext: FoundationDBReadContext,
) {
    val scanRequest = storeAction.request
    val valuesWithMeta = ArrayList<ValuesWithMetaData<DM>>(scanRequest.limit.toInt().coerceAtLeast(4))
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    val aggregator = scanRequest.aggregations?.let { scanRequest.dataModel.createRecordAggregator(it, scanRequest.select) }
    var lastEmittedKey: Key<DM>? = null
    var lastEmittedOrderKey: ByteArray? = null

    val pending = ArrayList<PendingScanRecord<DM>>(8)
    fun materialize(tr: Transaction, record: PendingScanRecord<DM>) {
        val (key, creationVersion, orderKey, preparedRead) = record
        val keyBytes = key.bytes
        val cacheReader = cache.cachedValueReader(dbIndex, key)

        val vwm = scanRequest.dataModel.readTransactionIntoValuesWithMetaData(
            tr = tr,
            creationVersion = creationVersion,
            tableDirs = tableDirs,
            key = key,
            select = scanRequest.select,
            toVersion = scanRequest.toVersion,
            cachedRead = cacheReader,
            readBudget = readContext.readBudget,
            decryptValue = valueDecryptor,
            preparedRead = preparedRead,
        )
        if (valuesWithMeta.size.toUInt() < scanRequest.limit && vwm != null) {
            valuesWithMeta.add(vwm)
            lastEmittedKey = key
            lastEmittedOrderKey = orderKey
        }

        aggregator?.aggregate {
            @Suppress("UNCHECKED_CAST")
            vwm?.values?.get(it as IsPropertyReference<Any, IsPropertyDefinition<Any>, *>)
                ?: tr.getValue(
                    tableDirs = tableDirs,
                    toVersion = scanRequest.toVersion,
                    keyBytes = keyBytes,
                    referenceBytes = it.toStorageByteArray(),
                    decryptValue = valueDecryptor
                ) { valueBytes, offset, length ->
                    valueBytes.convertToValue(it, offset, length)
                }
        }
    }
    fun drain(tr: Transaction) {
        for (record in pending) {
            readContext.requireUsable()
            materialize(tr, record)
            record.read?.release()
        }
        pending.clear()
    }

    val responseFetchType = try {
        processScan(
            scanRequest = scanRequest,
            tableDirs = tableDirs,
            readContext = readContext,
            scanComplete = ::drain,
        ) { tr, key, creationVersion, orderKey ->
            val read = if (scanRequest.select?.properties?.isEmpty() == true) null else {
                scanRequest.dataModel.prepareRecordRead(
                    tr, tableDirs, key.bytes, scanRequest.select, scanRequest.toVersion, readContext.readBudget,
                )
            }
            pending += PendingScanRecord(key, creationVersion, orderKey, read)
            if (pending.size == 8) drain(tr)
        }
    } finally {
        pending.forEach { it.read?.release() }
    }

    storeAction.response.complete(
        ValuesResponse(
            dataModel = scanRequest.dataModel,
            values = valuesWithMeta,
            aggregations = aggregator?.toResponse(),
            dataFetchType = responseFetchType,
            nextCursor = lastEmittedKey
                ?.takeIf { valuesWithMeta.size.toUInt() == scanRequest.limit }
                ?.let { scanRequest.createCursor(it, lastEmittedOrderKey) },
        )
    )
}

internal data class PendingScanRecord<DM : IsRootDataModel>(
    val key: Key<DM>,
    val creationVersion: ULong,
    val orderKey: ByteArray?,
    val read: PreparedRecordRead?,
)
