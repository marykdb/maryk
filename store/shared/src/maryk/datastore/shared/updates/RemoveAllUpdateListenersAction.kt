package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Clears all listeners for updates */
class RemoveAllUpdateListenersAction(
    val completion: CompletableDeferred<Unit>? = null
) : IsUpdateAction
