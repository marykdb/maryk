package maryk.core.query.requests

import maryk.core.models.RootObjectDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt64

/** Request for all changes from a version and later */
interface IsChangesRequest<DO: Any, out DM: RootObjectDataModel<*, DO, *>> : IsFetchRequest<DO, DM>, IsSelectRequest<DO, DM> {
    val fromVersion: UInt64

    companion object {
        internal fun <DM: Any> addFromVersion(index: Int, definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> UInt64?) {
            definitions.add(index, "fromVersion", NumberDefinition(type = UInt64), getter)
        }
    }
}
