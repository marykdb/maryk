package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.responses.updates.IsUpdateResponse

/**
 * Describes an update listener
 */
abstract class UpdateListener<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    val sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    fun close() {
        this.sendChannel.close()
    }

    /** Process [update] and sent out responses over channel */
    abstract suspend fun process(update: Update<DM, P>)
}
