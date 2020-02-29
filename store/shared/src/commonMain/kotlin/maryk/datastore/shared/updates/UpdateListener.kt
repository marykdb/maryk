package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.responses.updates.IsUpdateResponse

/**
 * Describes an update listener
 */
class UpdateListener<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val request: IsChangesRequest<DM, P, *>,
    val sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    fun close() {
        this.sendChannel.close()
    }
}
