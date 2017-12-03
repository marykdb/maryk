package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.NumberDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualReferenceDefinition
import maryk.core.properties.types.Key
import maryk.core.properties.types.numeric.UInt32
import maryk.core.query.DataModelPropertyContext

/** Defines a Scan from key request. */
interface IsScanRequest<DO: Any, out DM: RootDataModel<DO>> : IsFetchRequest<DO, DM> {
    val startKey: Key<DO>
    val limit: UInt32

    companion object {
        internal fun <DM: Any> addStartKey(definitions: PropertyDefinitions<DM>, getter: (DM) -> Key<Any>?) {
            definitions.add(1, "startKey", ContextualReferenceDefinition<DataModelPropertyContext>(
                    contextualResolver = { it!!.dataModel!!.key }
            ), getter)
        }

        internal fun <DM: Any> addLimit(definitions: PropertyDefinitions<DM>, getter: (DM) -> UInt32?) {
            definitions.add(6, "limit", NumberDefinition(
                    type = UInt32
            ), getter)
        }
    }
}