package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.responses.IsResponse

/** Request for all changes from a version and later */
interface IsChangesRequest<DM: IsRootDataModel<*>, RP: IsResponse> : IsFetchRequest<DM, RP> {
    val fromVersion: UInt64

    companion object {
        internal fun <DM: Any> addFromVersion(index: Int, definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> UInt64?) =
            definitions.add(index, "fromVersion", NumberDefinition(type = UInt64), getter)
    }
}
