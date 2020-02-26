package maryk.datastore.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import maryk.core.clock.HLC
import maryk.core.properties.types.Key

sealed class Update {
    data class Addition(val key: Key<*>, val version: HLC): Update()
    data class Deletion(val key: Key<*>, val version: HLC, val isHardDelete: Boolean): Update()
    data class Change(val key: Key<*>, val version: HLC): Update()
}

/** Actor which processes an update */
@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun CoroutineScope.processUpdateActor(): SendChannel<Update> =
    BroadcastChannel<Update>(Channel.BUFFERED).also {
        this.launch {
            it.asFlow().collect { update ->
                println(update)
            }
        }
    }
