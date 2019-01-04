@file:Suppress("EXPERIMENTAL_UNSIGNED_LITERALS", "EXPERIMENTAL_API_USAGE")

package maryk.datastore.memory.processors

import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.createScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.ChangesResponse
import maryk.datastore.memory.StoreAction
import maryk.datastore.memory.records.DataStore
import maryk.lib.extensions.compare.compareTo

internal typealias ScanChangesStoreAction<DM, P> = StoreAction<DM, P, ScanChangesRequest<DM, P>, ChangesResponse<DM>>
internal typealias AnyScanChangesStoreAction = ScanChangesStoreAction<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>

/** Processes a ScanRequest in a [storeAction] into a [dataStore] */
internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> processScanChangesRequest(
    storeAction: ScanChangesStoreAction<DM, P>,
    dataStore: DataStore<DM, P>
) {
    val scanRequest = storeAction.request
    val objectChanges = mutableListOf<DataObjectVersionedChange<DM>>()

    val scanRange = scanRequest.dataModel.createScanRange(scanRequest.filter, scanRequest.startKey?.bytes)

    val startIndex = dataStore.records.binarySearch { it.key.bytes.compareTo(scanRange.start) }.let {
        // If negative start at first entry point
        if (it < 0) {
            it * -1 + 1
        } else if (!scanRange.startInclusive) {
            it + 1 // Skip the match if not inclusive
        } else  { it }
    }

    for (index in startIndex until dataStore.records.size) {
        val record = dataStore.records[index]

        if (scanRange.keyOutOfRange(record.key.bytes)) {
            break
        }

        if (!scanRange.keyMatches(record.key.bytes)) {
            continue
        }

        if (scanRequest.filterData(record, scanRequest.toVersion)) {
            continue
        }

        scanRequest.dataModel.recordToObjectChanges(
            scanRequest.select,
            scanRequest.fromVersion,
            scanRequest.toVersion,
            record
        )?.let {
            // Only add if not null
            objectChanges += it
        }

        // Break when limit is found
        if (objectChanges.size.toUInt() == scanRequest.limit) break
    }

    storeAction.response.complete(
        ChangesResponse(
            dataModel = scanRequest.dataModel,
            changes = objectChanges
        )
    )
}
