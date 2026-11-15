package maryk.datastore.foundationdb.processors

import maryk.core.models.IsRootDataModel
import maryk.core.properties.references.IsPropertyReferenceForCache
import maryk.core.properties.types.Bytes
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.FoundationDBReadContext
import maryk.datastore.foundationdb.HistoricTableDirectories
import maryk.datastore.foundationdb.processors.helpers.prepareRecordRead
import maryk.datastore.foundationdb.processors.helpers.prepareRecordSelection
import maryk.foundationdb.Transaction
import maryk.datastore.shared.Cache
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions

internal typealias ScanChangesStoreAction<DM> = StoreAction<DM, ScanChangesRequest<DM>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootDataModel>

/** Processes a ScanChangesRequest in a [storeAction] into a [FoundationDBDataStore] */
internal fun <DM : IsRootDataModel> FoundationDBDataStore.processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM>,
    cache: Cache,
    readContext: FoundationDBReadContext,
) {
    val scanRequest = storeAction.request
    val objectChanges = ArrayList<DataObjectVersionedChange<DM>>(scanRequest.limit.toInt().coerceAtLeast(4))
    val dbIndex = getDataModelId(scanRequest.dataModel)
    val tableDirs = getTableDirs(dbIndex)

    scanRequest.checkMaxVersions(keepAllVersions)

    val pending = ArrayList<PendingScanRecord<DM>>(8)
    val selection = if (scanRequest.maxVersions == 1u) {
        scanRequest.dataModel.prepareRecordSelection(scanRequest.select, scanRequest.toVersion)
    } else null

    fun materialize(tr: Transaction, record: PendingScanRecord<DM>) {
            val (key, creationVersion, sortingKey, preparedRead) = record
            val cacheReader = { reference: IsPropertyReferenceForCache<*, *>, version: ULong, valueReader: () -> Any? ->
                cache.readValue(dbIndex, key, reference, version, valueReader)
            }

            val change = scanRequest.dataModel.readTransactionIntoObjectChanges(
                tr = tr,
                creationVersion = creationVersion,
                tableDirs = tableDirs,
                key = key,
                select = scanRequest.select,
                fromVersion = scanRequest.fromVersion,
                toVersion = scanRequest.toVersion,
                maxVersions = scanRequest.maxVersions,
                sortingKey = sortingKey,
                cachedRead = cacheReader,
                decryptValue = valueDecryptor,
                readBudget = readContext.readBudget,
                preparedRead = preparedRead,
            )
            val updated = if (scanRequest.needsSoftDeleteFallback() && tableDirs is HistoricTableDirectories) {
                addSoftDeleteChangeIfMissing(
                    tr = tr,
                    tableDirs = tableDirs,
                    key = key,
                    fromVersion = scanRequest.fromVersion,
                    objectChange = change,
                    sortingKey = sortingKey,
                    toVersion = scanRequest.toVersion,
                    maxVersions = scanRequest.maxVersions,
                )
            } else {
                change
            }
            if (updated != null) {
                objectChanges += updated
            } else if (storeAction.isFlowSnapshotRead) {
                objectChanges += DataObjectVersionedChange(
                    key = key,
                    sortingKey = sortingKey?.let(::Bytes),
                    changes = emptyList()
                )
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
    val dataFetchType = try {
        processScan(
            scanRequest = scanRequest,
            tableDirs = tableDirs,
            readContext = readContext,
            scanComplete = ::drain,
        ) { tr, key, creationVersion, sortingKey ->
            if (selection == null) {
                materialize(tr, PendingScanRecord(key, creationVersion, sortingKey, null))
            } else {
                val read = scanRequest.dataModel.prepareRecordRead(
                    tr, tableDirs, key.bytes, selection, scanRequest.toVersion, readContext.readBudget,
                )
                pending += PendingScanRecord(key, creationVersion, sortingKey, read)
                if (pending.size == 8) drain(tr)
            }
        }
    } finally {
        pending.forEach { it.read?.release() }
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = scanRequest.dataModel,
            changes = objectChanges,
            dataFetchType = dataFetchType,
        )
    )
}

private fun ScanChangesRequest<*>.needsSoftDeleteFallback() =
    toVersion != null || maxVersions > 1u || !filterSoftDeleted
