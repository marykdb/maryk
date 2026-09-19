package maryk.datastore.shared.updates

import kotlinx.coroutines.CompletableDeferred

/** Add an update [listener] */
class AddUpdateListenerAction(
    val dataModelId: UInt,
    val listener: UpdateListener<*, *>,
    val completion: CompletableDeferred<Unit>? = null
) : IsUpdateAction
