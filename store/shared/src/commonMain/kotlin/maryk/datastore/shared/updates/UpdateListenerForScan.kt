package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.values.Values
import maryk.datastore.shared.AbstractDataStore

/** Update listener for scans */
class UpdateListenerForScan<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val request: ScanChangesRequest<DM, P>,
    val scanRange: KeyScanRanges,
    matchingKeys: List<Key<DM>>,
    sortedValues: List<Values<DM, P>>?,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) : UpdateListener<DM, P>(matchingKeys.toMutableList(), sendChannel) {
    val sortedValues = sortedValues?.toMutableList()

    override suspend fun process(
        update: Update<DM, P>,
        dataStore: AbstractDataStore
    ) {
        if ((!sortedValues.isNullOrEmpty() || scanRange.keyWithinRanges(update.key.bytes, 0)) && scanRange.matchesPartials(update.key.bytes)) {
            // Only process object requests or change requests if the version is after or equal to from version
            update.process(request, matchingKeys, sortedValues, dataStore, sendChannel)
        }
    }
}
