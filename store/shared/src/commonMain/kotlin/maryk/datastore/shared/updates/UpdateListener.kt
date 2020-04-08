package maryk.datastore.shared.updates

import kotlinx.coroutines.channels.SendChannel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsChangesRequest
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.values.Values
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.updates.Update.Change

/**
 * Describes an update listener
 */
abstract class UpdateListener<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, RQ: IsChangesRequest<DM, P, *>>(
    val request: RQ,
    val matchingKeys: MutableList<Key<DM>>,
    val sendChannel: SendChannel<IsUpdateResponse<DM, P>>
) {
    fun close() {
        this.sendChannel.close()
    }

    /** Process [update] and sent out responses over channel */
    abstract suspend fun process(
        update: Update<DM, P>,
        dataStore: AbstractDataStore
    )

    /** Add [values] at [key] and return sort index or null if it should not be added */
    abstract fun addValues(key: Key<DM>, values: Values<DM, P>): Int?

    /** Remove [key] from local index */
    open fun removeKey(key: Key<DM>): Int {
        val index = matchingKeys.indexOf(key)

        if (index >= 0) {
            matchingKeys.removeAt(index)
        }
        return index
    }

    /**
     * Change order for values if needed and return new or current index.
     * Calls changedHandler with an index at which index the value should be or
     * null if it was deleted
     */
    abstract suspend fun changeOrder(change: Change<DM, P>, changedHandler: suspend (Int?) -> Unit)
}
