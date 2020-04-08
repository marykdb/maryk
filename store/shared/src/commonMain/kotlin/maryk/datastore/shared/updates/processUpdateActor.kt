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
import maryk.datastore.shared.AbstractDataStore

/** Actor which processes an update */
@OptIn(
    ExperimentalCoroutinesApi::class, FlowPreview::class
)
internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> AbstractDataStore.processUpdateActor(): SendChannel<Update<DM, P>> =
    BroadcastChannel<Update<DM, P>>(Channel.BUFFERED).also {
        this.launch {
            it.asFlow().collect { update ->
                @Suppress("UNCHECKED_CAST")
                val dataModelListeners = updateListeners[getDataModelId(update.dataModel)] as MutableList<UpdateListener<DM, P, *>>?

                if (dataModelListeners != null) {
                    for (updateListener in dataModelListeners) {
                        updateListener.process(update, this@processUpdateActor)
                    }
                }
            }
        }
    }
