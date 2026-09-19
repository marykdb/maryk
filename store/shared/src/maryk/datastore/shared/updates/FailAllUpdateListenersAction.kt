package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Terminates all live listeners when their incremental backend cursor can no longer be resumed safely. */
class FailAllUpdateListenersAction(
    val cause: Throwable,
    val completion: CompletableDeferred<Unit>? = null,
) : IsUpdateAction
