package maryk.core.query.requests

import maryk.core.properties.graph.RootPropRefGraph
import maryk.core.models.RootObjectDataModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.ObjectPropertyDefinitions

/**
 * For only returning selected properties defined by PropRefGraph
 */
interface IsSelectRequest<DO: Any, out DM: RootObjectDataModel<DO, *>> : IsFetchRequest<DO, DM> {
    val select: RootPropRefGraph<DO>?

    companion object {
        internal fun <DM: Any> addSelect(index: Int, definitions: ObjectPropertyDefinitions<DM>, getter: (DM) -> RootPropRefGraph<*>?) {
            definitions.add(index, "select", EmbeddedObjectDefinition(dataModel = { RootPropRefGraph }), getter)
        }
    }
}
