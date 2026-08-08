package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Removes a listener that was registered before its initial query completed. */
internal class RemovePendingUpdateListenerAction(
    val dataModelId: UInt,
    val listener: PendingUpdateListener,
    val completion: CompletableDeferred<Unit>? = null,
) : IsUpdateAction
