package maryk.datastore.shared.updates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.core.query.requests.IsGetRequest
import maryk.datastore.shared.AbstractDataStore

/** Actor which processes an update */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun AbstractDataStore.processUpdateActor(): SendChannel<Update<*, *>> =
    BroadcastChannel<Update<*, *>>(Channel.BUFFERED).also {
        this.launch {
            it.asFlow().collect { update ->
                val dataModelListeners = updateListeners[getDataModelId(update.dataModel)]

                if (dataModelListeners != null) {
                    for (updateListener in dataModelListeners) {
                        if (updateListener.request is IsGetRequest<*, *, *>) {
                            @Suppress("UNCHECKED_CAST")
                            (update as Update<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>).processGetRequest(
                                request = updateListener.request,
                                updateListener = updateListener
                            )
                        }
                    }
                }
            }
        }
    }
