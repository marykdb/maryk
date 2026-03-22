package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Removes a specific update [listener] */
class RemoveUpdateListenerAction(
    val dataModelId: UInt,
    val listener: UpdateListener<*, *>,
    val completion: CompletableDeferred<Unit>? = null
) : IsUpdateAction
