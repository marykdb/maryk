package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.graph.RootPropRefGraph

/**
 * For only returning selected properties defined by PropRefGraph
 */
interface IsSelectRequest<DM: IsRootDataModel<*>> : IsFetchRequest<DM> {
    val select: RootPropRefGraph<DM>?

    companion object {
        internal fun <DM: Any> addSelect(index: Int, definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> RootPropRefGraph<*>?) {
            definitions.add(index, "select", EmbeddedObjectDefinition(dataModel = { RootPropRefGraph }), getter)
        }
    }
}
