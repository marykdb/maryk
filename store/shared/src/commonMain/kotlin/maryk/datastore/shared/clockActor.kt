package maryk.datastore.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import maryk.core.clock.HLC

/** ClockAction to perform */
class DeferredClock(
    val toCompare: HLC? = null
) {
    val completableDeferred = CompletableDeferred<HLC>()
}

/** Actor which holds current highest time */
internal expect fun CoroutineScope.clockActor(): SendChannel<DeferredClock>
