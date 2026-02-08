package maryk.datastore.shared.updates

import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsGetRequest
import maryk.core.query.responses.IsDataResponse
import maryk.core.values.Values
import maryk.datastore.shared.IsDataStore
import maryk.datastore.shared.updates.Update.Change

/** Update listener for get requests */
class UpdateListenerForGet<DM: IsRootDataModel, RP: IsDataResponse<DM>>(
    request: IsGetRequest<DM, RP>,
    response: RP
) : UpdateListener<DM, IsGetRequest<DM, RP>>(
    request,
    response
) {
    override suspend fun process(
        update: Update<DM>,
        dataStore: IsDataStore
    ) {
        if (request.keys.contains(update.key)) {
            update.process(this, dataStore, sendFlow)
        }
    }

    override fun addValues(key: Key<DM>, values: Values<DM>) =
        matchingKeys.value.indexOfFirst { it compareTo key == 0 }.let { index ->
            // Only insert keys which were found in the matching keys
            if (index < 0) null else index
        }

    override suspend fun changeOrder(change: Change<DM>, changedHandler: suspend (Int?, Boolean) -> Unit) {
        val keyIndex = matchingKeys.value.indexOfFirst { it compareTo change.key == 0 }

        if (keyIndex >= 0) {
            changedHandler(if (keyIndex >= 0) keyIndex else null, false)
        }
    }
}
