package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import maryk.core.models.IsRootDataModel
import maryk.datastore.shared.IsDataStore

internal suspend fun IsDataStore.startProcessUpdateFlow(updateSendChannel: Flow<IsUpdateAction>, updateSendChannelHasStarted: CompletableDeferred<Unit>) {
    val updateListeners = mutableMapOf<UInt, MutableList<UpdateListener<*, *>>>()

    (updateSendChannel)
        .onStart { updateSendChannelHasStarted.complete(Unit) }
        .onCompletion {
            updateListeners.values.forEach { it.forEach(UpdateListener<*, *>::close) }
            updateListeners.clear()
        }.collect { update ->
            when (update) {
                is Update<*> -> {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val dataModelListeners =
                            updateListeners[dataModelIdsByString[update.dataModel.Meta.name]] as? MutableList<UpdateListener<IsRootDataModel, *>>?

                        if (dataModelListeners != null) {
                            for (updateListener in dataModelListeners) {
                                @Suppress("UNCHECKED_CAST")
                                updateListener.process(
                                    update as Update<IsRootDataModel>,
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
                    updateListeners.values.forEach { it.forEach(UpdateListener<*, *>::close) }
                    updateListeners.clear()
                }
                else -> throw RuntimeException("Unknown update listener action: $update")
            }
        }
}
