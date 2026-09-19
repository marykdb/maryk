package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.changes.ObjectCreate
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.responses.FetchByKey
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.AdditionUpdate
import maryk.core.query.responses.updates.ChangeUpdate
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.datastore.memory.IsStoreFetcher
import maryk.datastore.shared.StoreAction
import maryk.datastore.shared.checkMaxVersions
import maryk.datastore.shared.checkToVersion

internal typealias GetUpdatesStoreAction<DM> = StoreAction<DM, GetUpdatesRequest<DM>, UpdatesResponse<DM>>
internal typealias AnyGetUpdatesStoreAction = GetUpdatesStoreAction<IsRootDataModel>

/** Processes a GetUpdatesRequest in a [storeAction] into a dataStore from [dataStoreFetcher] */
internal fun <DM : IsRootDataModel> processGetUpdatesRequest(
    storeAction: GetUpdatesStoreAction<DM>,
    dataStoreFetcher: IsStoreFetcher<DM>
) {
    val getRequest = storeAction.request

    val dataStore = dataStoreFetcher.invoke(getRequest.dataModel)

    getRequest.checkToVersion(dataStore.keepAllVersions)
    getRequest.checkMaxVersions(dataStore.keepAllVersions)
    val toVersion = getRequest.toVersion?.let { HLC(it) }
    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher, toVersion)

    val expectedSize = getRequest.keys.size.coerceAtLeast(4)
    val matchingKeys = ArrayList<Key<DM>>(expectedSize)
    val updates = ArrayList<IsUpdateResponse<DM>>(expectedSize + 1)
    var lastResponseVersion = 0uL
    var insertionIndex = -1

    for (key in getRequest.keys) {
        val records = dataStore.getRecordHistoryByKey(key.bytes, toVersion)
        val record = records.lastOrNull() ?: continue

        if (getRequest.shouldBeFiltered(record, toVersion, recordFetcher)) {
            continue
        }

        insertionIndex++

        matchingKeys.add(record.key)
        lastResponseVersion = maxOf(lastResponseVersion, records.maxOf { it.lastVersion.timestamp })

        getRequest.dataModel.recordHistoryToVersionedChanges(
            select = getRequest.select,
            fromVersion = getRequest.fromVersion,
            toVersion = getRequest.toVersion,
            maxVersions = getRequest.maxVersions,
            sortingKey = null,
            historyRecords = records
        ).forEach { (historyRecord, versionedChange) ->
            val changes = versionedChange.changes

            if (changes.contains(ObjectCreate)) {
                getRequest.dataModel.recordToValueWithMeta(
                    getRequest.select,
                    HLC(versionedChange.version),
                    historyRecord
                )?.let { valuesWithMeta ->
                    updates += AdditionUpdate(
                        key = historyRecord.key,
                        version = versionedChange.version,
                        firstVersion = valuesWithMeta.firstVersion,
                        insertionIndex = insertionIndex,
                        isDeleted = valuesWithMeta.isDeleted,
                        values = valuesWithMeta.values
                    )
                }
            } else {
                updates += ChangeUpdate(
                    key = historyRecord.key,
                    version = versionedChange.version,
                    index = insertionIndex,
                    changes = changes
                )
            }
        }
    }

    // Sort all updates on version, they are before sorted on data object order and then version
    updates.sortBy { it.version }

    lastResponseVersion = minOf(getRequest.toVersion ?: ULong.MAX_VALUE, lastResponseVersion)

    updates.add(0,
        OrderedKeysUpdate(
            version = lastResponseVersion,
            keys = matchingKeys
        )
    )

    storeAction.response.complete(
        UpdatesResponse(
            dataModel = getRequest.dataModel,
            updates = updates,
            dataFetchType = FetchByKey,
        )
    )
}
