package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.processors.datastore.scanRange.KeyScanRanges
import maryk.core.processors.datastore.scanRange.ScanRange
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.requests.IsScanRequest
import maryk.core.query.requests.ScanChangesRequest
import maryk.core.query.responses.updates.IsUpdateResponse

/** Update listener for scans */
class UpdateListenerForScan<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val request: ScanChangesRequest<DM, P>,
    val scanRange: KeyScanRanges,
    sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) : UpdateListener<DM, P>(sendChannel) {
    override suspend fun process(update: Update<DM, P>) {
        if (scanRange.keyWithinRanges(update.key.bytes, 0) && scanRange.matchesPartials(update.key.bytes)) {
            // Only process object requests or change requests if the version is after or equal to from version
            update.process(request, sendChannel)
        }
    }
}
