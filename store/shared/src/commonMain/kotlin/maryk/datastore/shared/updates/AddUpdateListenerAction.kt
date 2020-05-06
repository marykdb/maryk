package maryk.datastore.shared.updates

/** Add an update [listener] */
class AddUpdateListenerAction(
    val dataModelId: UInt,
    val listener: UpdateListener<*, *, *>
) : IsUpdateAction
