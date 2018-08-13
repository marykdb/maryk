package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesPropertyDefinitionWrapper
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.responses.IsResponse

/** Request for all versioned changes from a version and later */
interface IsVersionedChangesRequest<DM: IsRootDataModel<*>, RP: IsResponse> : IsChangesRequest<DM, RP> {
    val maxVersions: UInt32

    companion object {
        internal fun <DM: Any> addMaxVersions(index: Int, definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> UInt32?): FixedBytesPropertyDefinitionWrapper<UInt32, UInt32, IsPropertyContext, NumberDefinition<UInt32>, DM> =
            definitions.add(index, "maxVersions",
                NumberDefinition(
                    type = UInt32,
                    maxValue = 1000.toUInt32()
                ),
                getter
            )
    }
}
