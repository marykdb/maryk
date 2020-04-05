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
import maryk.lib.extensions.toHex

/** Update listener for scans */
class UpdateListenerForScan<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val request: ScanChangesRequest<DM, P>,
    val scanRange: KeyScanRanges,
    matchingKeys: List<Key<DM>>,
    sortedValues: List<Values<DM, P>>?,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) : UpdateListener<DM, P>(matchingKeys.toMutableList(), sendChannel) {
    private val sortedValues = sortedValues?.toMutableList()

    override suspend fun process(
        update: Update<DM, P>,
        dataStore: AbstractDataStore
    ) {
        if ((!sortedValues.isNullOrEmpty() || !scanRange.keyBeforeStart(update.key.bytes, 0)) && scanRange.keyWithinRanges(update.key.bytes, 0) && scanRange.matchesPartials(update.key.bytes)) {
            update.process(request, matchingKeys, sortedValues, dataStore, sendChannel)
        }
    }
}
