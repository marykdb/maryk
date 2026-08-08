package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Replaces a pending listener with its initialized listener. */
internal class ActivatePendingUpdateListenerAction(
    val dataModelId: UInt,
    val pendingListener: PendingUpdateListener,
    val listener: UpdateListener<*, *>,
    val snapshotBoundary: FlowSnapshotBoundary = FlowSnapshotBoundary(Long.MIN_VALUE),
    val completion: CompletableDeferred<Unit>? = null,
    val onActivated: (suspend () -> Unit)? = null,
) : IsUpdateAction
