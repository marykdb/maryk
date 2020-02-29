package maryk.datastore.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.models.IsRootDataModel
import maryk.core.properties.types.Key

sealed class Update {
    data class Addition<DM: IsRootDataModel<*>>(val key: Key<DM>, val version: HLC): Update()
    data class Deletion<DM: IsRootDataModel<*>>(val key: Key<DM>, val version: HLC, val isHardDelete: Boolean): Update()
    data class Change<DM: IsRootDataModel<*>>(val key: Key<DM>, val version: HLC): Update()
}

/** Actor which processes an update */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun UpdateProcessor.processUpdateActor(): SendChannel<Update> =
    BroadcastChannel<Update>(Channel.BUFFERED).also {
        this.launch {
            it.asFlow().collect { update ->
                updateListeners.forEach {
                    it.send(update)
                }
            }
        }
    }
