package maryk.datastore.shared.updates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onStart
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.types.Key
import maryk.core.query.requests.IsUpdatesRequest
import maryk.core.query.responses.UpdatesResponse
import maryk.core.query.responses.updates.IsUpdateResponse
import maryk.core.query.responses.updates.OrderedKeysUpdate
import maryk.core.values.Values
import maryk.datastore.shared.AbstractDataStore
import maryk.datastore.shared.updates.Update.Change

/** Listener for updates on a data store */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
abstract class UpdateListener<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions, RQ: IsUpdatesRequest<DM, P, *>>(
    val request: RQ,
    val updatesResponse: UpdatesResponse<DM, P>
) {
    protected val sendChannel = BroadcastChannel<IsUpdateResponse<DM, P>>(Channel.BUFFERED)

    val matchingKeys = (updatesResponse.updates.firstOrNull() as? OrderedKeysUpdate<DM, P>)?.keys?.toMutableList() ?: mutableListOf()
    val lastResponseVersion = (updatesResponse.updates.firstOrNull() as? OrderedKeysUpdate<DM, P>)?.version ?: 0uL

    // True if the listener filters on mutable values
    val filterContainsMutableValues: Boolean = request.where?.singleReference {
        !it.propertyDefinition.required || !it.propertyDefinition.final
    } != null

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
     * Calls changedHandler with an index at which index the value should be and boolean if order changed or
     * null if it was deleted
     */
    abstract suspend fun changeOrder(change: Change<DM, P>, changedHandler: suspend (Int?, Boolean) -> Unit)

    /** Get flow with update responses */
    fun getFlow() = sendChannel.asFlow().onStart {
        for (update in updatesResponse.updates) {
            emit(update)
        }
    }

    fun close() {
        this.sendChannel.close()
    }
}
