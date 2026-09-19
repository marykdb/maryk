package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Registers a listener before its initial query response is available. */
internal class AddPendingUpdateListenerAction(
    val dataModelId: UInt,
    val listener: PendingUpdateListener,
    val completion: CompletableDeferred<Unit>? = null,
) : IsUpdateAction
