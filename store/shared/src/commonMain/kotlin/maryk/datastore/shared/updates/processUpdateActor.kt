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
import maryk.datastore.shared.updates.Update.Addition
import maryk.datastore.shared.updates.Update.Change
import maryk.datastore.shared.updates.Update.Deletion

/** Actor which processes an update */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun AbstractDataStore.processUpdateActor(): SendChannel<Update<*>> =
    BroadcastChannel<Update<*>>(Channel.BUFFERED).also {
        this.launch {
            it.asFlow().collect { update ->
                @Suppress("UNCHECKED_CAST")
                val dataModelListeners = updateListeners[getDataModelId(update.dataModel)] as MutableList<UpdateListener<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>?

                if (dataModelListeners != null) {
                    for (updateListener in dataModelListeners) {
                        when (update) {
                            is Addition<*> -> {
                                if (updateListener.request is IsGetRequest<*, *, *>) {
                                    updateListener.request.keys.contains(update.key)
                                    @Suppress("UNCHECKED_CAST")
                                    updateListener.sendChannel.send(update as Update<IsRootValuesDataModel<PropertyDefinitions>>)
                                }
                                println("ADD $update")
                            }
                            is Deletion<*> -> {
                                if (updateListener.request is IsGetRequest<*, *, *>) {
                                    updateListener.request.keys.contains(update.key)
                                    @Suppress("UNCHECKED_CAST")
                                    updateListener.sendChannel.send(update as Update<IsRootValuesDataModel<PropertyDefinitions>>)
                                }
                                println("DEL $update")
                            }
                            is Change<*> -> {
                                if (updateListener.request is IsGetRequest<*, *, *>) {
                                    updateListener.request.keys.contains(update.key)
                                    @Suppress("UNCHECKED_CAST")
                                    updateListener.sendChannel.send(update as Update<IsRootValuesDataModel<PropertyDefinitions>>)
                                }
                                println("CHANGE $update")
                            }
                        }
                    }
                }
            }
        }
    }
