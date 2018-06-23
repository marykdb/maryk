package maryk.core.query.requests

import maryk.core.objects.RootDataModel
import maryk.core.objects.graph.RootGraph
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.PropertyDefinitions

/**
 * For only returning selected properties defined by Graph
 */
interface IsSelectRequest<DO: Any, out DM: RootDataModel<DO, *>> : IsFetchRequest<DO, DM> {
    val select: RootGraph<DO>?

    companion object {
        internal fun <DM: Any> addSelect(index: Int, definitions: PropertyDefinitions<DM>, getter: (DM) -> RootGraph<*>?) {
            definitions.add(index, "select", EmbeddedObjectDefinition(dataModel = { RootGraph }), getter)
        }
    }
}
