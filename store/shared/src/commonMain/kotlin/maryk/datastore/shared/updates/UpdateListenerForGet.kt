package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.requests.GetUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.values.Values
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.updates.Update.Change

/** Update listener for get requests */
class UpdateListenerForGet<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    request: GetUpdatesRequest<DM, P>,
    updatesResponse: UpdatesResponse<DM, P>
) : UpdateListener<DM, P, GetUpdatesRequest<DM, P>>(
    request,
    updatesResponse
) {
    override suspend fun process(
        update: Update<DM, P>,
        dataStore: AbstractDataStore
    ) {
        if (request.keys.contains(update.key)) {
            update.process(this, dataStore, sendChannel)
        }
    }

    override fun addValues(key: Key<DM>, values: Values<DM, P>) =
        matchingKeys.binarySearch { it.compareTo(key) }.let {
            // Only insert keys which were found in the matching keys
            if (it < 0) null else it
        }

    override suspend fun changeOrder(change: Change<DM, P>, changedHandler: suspend (Int?, Boolean) -> Unit) {
        val keyIndex = matchingKeys.indexOfFirst { it.compareTo(change.key) == 0 }

        if (keyIndex >= 0) {
            changedHandler(if (keyIndex >= 0) keyIndex else null, false)
        }
    }
}
