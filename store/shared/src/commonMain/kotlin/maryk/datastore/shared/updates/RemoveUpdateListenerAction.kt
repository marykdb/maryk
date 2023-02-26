package maryk.datastore.shared.updates

/** Removes a specific update [listener] */
class RemoveUpdateListenerAction(
    val dataModelId: UInt,
    val listener: UpdateListener<*, *>
) : IsUpdateAction
