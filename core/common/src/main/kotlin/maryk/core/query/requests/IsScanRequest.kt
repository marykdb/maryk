package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.properties.types.numeric.toUInt32
import maryk.core.query.DataModelPropertyContext

/** Defines a Scan from key request. */
interface IsScanRequest<DM: IsRootDataModel<*>> : IsFetchRequest<DM> {
    val startKey: Key<DM>
    val limit: UInt32

    companion object {
        internal fun <DO: IsScanRequest<*>, DM: IsRootDataModel<*>> addStartKey(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> Key<DM>?) {
            definitions.add(1, "startKey", ContextualReferenceDefinition<DataModelPropertyContext>(
                contextualResolver = {
                    it?.dataModel ?: throw ContextNotFoundException()
                }
            ), getter)
        }

        internal fun <DO: Any> addLimit(definitions: ObjectPropertyDefinitions<DO>, getter: (DO) -> UInt32?) {
            definitions.add(7, "limit", NumberDefinition(
                default = 100.toUInt32(),
                type = UInt32
            ), getter)
        }
    }
}
