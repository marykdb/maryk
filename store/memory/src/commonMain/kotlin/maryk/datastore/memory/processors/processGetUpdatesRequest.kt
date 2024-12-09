package maryk.datastore.memory.processors

import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.models.fromChanges
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

    val recordFetcher = createStoreRecordFetcher(dataStoreFetcher)

    getRequest.checkToVersion(dataStore.keepAllVersions)
    getRequest.checkMaxVersions(dataStore.keepAllVersions)

    val matchingKeys = mutableListOf<Key<DM>>()
    val updates = mutableListOf<IsUpdateResponse<DM>>()
    var lastResponseVersion = 0uL
    var insertionIndex = -1

    for (key in getRequest.keys) {
        val index = dataStore.records.binarySearch { it.key compareTo key }

        // Only return if found
        if (index > -1) {
            val record = dataStore.records[index]

            if (getRequest.shouldBeFiltered(record, getRequest.toVersion?.let { HLC(it) }, recordFetcher)) {
                continue
            }

            insertionIndex++

            matchingKeys.add(record.key)
            lastResponseVersion = maxOf(lastResponseVersion, record.lastVersion.timestamp)

            getRequest.dataModel.recordToObjectChanges(
                getRequest.select,
                getRequest.fromVersion,
                getRequest.toVersion,
                getRequest.maxVersions,
                null,
                record
            )?.let { objectChange ->
                updates += objectChange.changes.map { versionedChange ->
                    val changes = versionedChange.changes

                    if (changes.contains(ObjectCreate)) {
                        val addedValues = getRequest.dataModel.fromChanges(null, changes)

                        AdditionUpdate(
                            key = objectChange.key,
                            version = versionedChange.version,
                            firstVersion = versionedChange.version,
                            insertionIndex = insertionIndex,
                            isDeleted = false,
                            values = addedValues
                        )
                    } else {
                        ChangeUpdate(
                            key = objectChange.key,
                            version = versionedChange.version,
                            index = insertionIndex,
                            changes = changes
                        )
                    }
                }
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
