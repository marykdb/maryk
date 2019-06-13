@file:Suppress("EXPERIMENTAL_API_USAGE")

package maryk.datastore.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import maryk.core.clock.HLC

internal actual fun CoroutineScope.clockActor(): SendChannel<DeferredClock> = actor {
    var currentHighestTime = HLC()

    for (action in channel) { // iterate over incoming messages
        try {
            currentHighestTime = currentHighestTime.calculateMaxTimeStamp(action.toCompare)

            action.completableDeferred.complete(currentHighestTime)
        } catch (e: Throwable) {
            action.completableDeferred.completeExceptionally(e)
        }
    }
}
