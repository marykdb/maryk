package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import maryk.core.models.IsRootValuesDataModel
import maryk.core.properties.PropertyDefinitions
import maryk.datastore.shared.IsDataStore

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal suspend fun IsDataStore.startProcessUpdateFlow(updateSendChannel: SendChannel<IsUpdateAction>, updateSendChannelHasStarted: CompletableDeferred<Unit>) {
    val updateListeners = mutableMapOf<UInt, MutableList<UpdateListener<*, *, *>>>()

    (updateSendChannel as BroadcastChannel<IsUpdateAction>).asFlow()
        .onStart { updateSendChannelHasStarted.complete(Unit) }
        .onCompletion {
            updateListeners.values.forEach { it.forEach(UpdateListener<*, *, *>::close) }
            updateListeners.clear()
        }.collect { update ->
            when (update) {
                is Update<*, *> -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val dataModelListeners =
                            updateListeners[dataModelIdsByString[update.dataModel.name]] as? MutableList<UpdateListener<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions, *>>?

                        if (dataModelListeners != null) {
                            for (updateListener in dataModelListeners) {
                                @Suppress("UNCHECKED_CAST")
                                updateListener.process(
                                    update as Update<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>,
                                    this
                                )
                            }
                        }
                    } catch (e: Throwable) {
                        throw RuntimeException(e)
                    }

                }
                is AddUpdateListenerAction -> {
                    val dataModelListeners =
                        updateListeners.getOrPut(update.dataModelId) { mutableListOf() }

                    dataModelListeners += update.listener
                }
                is RemoveUpdateListenerAction -> {
                    val dataModelListeners =
                        updateListeners.getOrPut(update.dataModelId) { mutableListOf() }

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
