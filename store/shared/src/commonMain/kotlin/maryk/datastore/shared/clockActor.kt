package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
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

/** ClockAction to perform */
class DeferredClock(
    val toCompare: HLC? = null
) {
    val completableDeferred = CompletableDeferred<HLC>()
}

/** Actor which holds current highest time */
@OptIn(
    ExperimentalCoroutinesApi::class, FlowPreview::class
)
internal fun CoroutineScope.clockActor(): SendChannel<DeferredClock> =
    BroadcastChannel<DeferredClock>(Channel.BUFFERED).also {
        this.launch {
            var currentHighestTime = HLC()
            it.asFlow().collect { action ->
                try {
                    currentHighestTime = currentHighestTime.calculateMaxTimeStamp(action.toCompare)
                    action.completableDeferred.complete(currentHighestTime)
                } catch (e: Throwable) {
                    action.completableDeferred.completeExceptionally(e)
                }
            }
        }
    }
