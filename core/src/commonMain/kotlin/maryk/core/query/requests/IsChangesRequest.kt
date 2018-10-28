package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.responses.IsResponse

/** Request for all changes from a version and later */
@Suppress("EXPERIMENTAL_API_USAGE")
interface IsChangesRequest<DM: IsRootDataModel<*>, RP: IsResponse> : IsFetchRequest<DM, RP> {
    val fromVersion: ULong

    companion object {
        internal fun <DM: Any> addFromVersion(index: Int, definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> ULong?) =
            definitions.add(index, "fromVersion", NumberDefinition(type = UInt64), getter)
    }
}
