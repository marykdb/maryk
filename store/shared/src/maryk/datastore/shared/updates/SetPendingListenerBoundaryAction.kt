package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Makes a pending listener discard updates already represented by its snapshot. */
internal class SetPendingListenerBoundaryAction(
    val dataModelId: UInt,
    val listener: PendingUpdateListener,
    val boundary: FlowSnapshotBoundary,
    val completion: CompletableDeferred<Unit>? = null,
) : IsUpdateAction
