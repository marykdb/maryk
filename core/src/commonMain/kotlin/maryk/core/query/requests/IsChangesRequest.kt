package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.IsPropertyContext
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.wrapper.FixedBytesDefinitionWrapper
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.UInt64
import maryk.core.query.responses.IsResponse

/** Request for all versioned changes from a version and later */
interface IsChangesRequest<DM : IsRootDataModel<P>, P : PropertyDefinitions, RP : IsResponse> :
    IsFetchRequest<DM, P, RP> {
    val fromVersion: ULong
    val maxVersions: UInt

    companion object {
        internal fun <DM : Any> addMaxVersions(
            index: UInt,
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> UInt?
        ): FixedBytesDefinitionWrapper<UInt, UInt, IsPropertyContext, NumberDefinition<UInt>, DM> =
            definitions.add(
                index, "maxVersions",
                NumberDefinition(
                    type = UInt32,
                    maxValue = 1000u
                ),
                getter
            )

        internal fun <DM : Any> addFromVersion(
            index: UInt,
            definitions: ObjectPropertyDefinitions<DM>,
            getter: (DM) -> ULong?
        ) =
            definitions.add(index, "fromVersion", NumberDefinition(type = UInt64), getter)
    }
}
