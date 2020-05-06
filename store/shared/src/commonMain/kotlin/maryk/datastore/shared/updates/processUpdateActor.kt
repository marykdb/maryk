package maryk.datastore.shared.updates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.shared.AbstractDataStore

/** Actor which processes an update */
@OptIn(
    ExperimentalCoroutinesApi::class, FlowPreview::class
)
internal fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> AbstractDataStore.processUpdateActor(): SendChannel<IsUpdateAction> =
    BroadcastChannel<IsUpdateAction>(Channel.BUFFERED).also {
        this.launch {
            val updateListeners = mutableMapOf<UInt, MutableList<UpdateListener<*, *, *>>>()

            it.asFlow().onCompletion {
                updateListeners.values.forEach { it.forEach(UpdateListener<*, *, *>::close) }
                updateListeners.clear()
            }.collect { update ->
                when (update) {
                    is Update<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val dataModelListeners = updateListeners[getDataModelId(update.dataModel)] as MutableList<UpdateListener<DM, P, *>>?

                        if (dataModelListeners != null) {
                            for (updateListener in dataModelListeners) {
                                @Suppress("UNCHECKED_CAST")
                                updateListener.process(update as Update<DM, P>, this@processUpdateActor)
                            }
                        }
                    }
                    is AddUpdateListenerAction -> {
                        val dataModelListeners = updateListeners.getOrPut(update.dataModelId) { mutableListOf() }

                        dataModelListeners += update.listener
                    }
                    is RemoveUpdateListenerAction -> {
                        val dataModelListeners = updateListeners.getOrPut(update.dataModelId) { mutableListOf() }

                        update.listener.close()
                        dataModelListeners -= update.listener
                    }
                    is RemoveAllUpdateListenersAction -> {
                        updateListeners.values.forEach { it.forEach(UpdateListener<*, *, *>::close) }
                        updateListeners.clear()
                    }
                    else -> throw RuntimeException("Unknown update listener action: $update")
                }
            }
        }
    }
