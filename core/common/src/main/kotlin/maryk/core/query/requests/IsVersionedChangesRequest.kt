package maryk.core.query.requests

import maryk.core.models.RootObjectDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32

/** Request for all versioned changes from a version and later */
interface IsVersionedChangesRequest<DO: Any, out DM: RootObjectDataModel<DO, *>> : IsChangesRequest<DO, DM> {
    val maxVersions: UInt32

    companion object {
        internal fun <DM: Any> addMaxVersions(index: Int, definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> UInt32?) {
            definitions.add(index, "maxVersions",
                NumberDefinition(
                    type = UInt32,
                    maxValue = 1000.toUInt32()
                ),
                getter
            )
        }
    }
}
