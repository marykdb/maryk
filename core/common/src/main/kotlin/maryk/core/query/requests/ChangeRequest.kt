package maryk.core.query.requests

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.SubModelDefinition
import maryk.core.query.changes.DataObjectChange

/**
 * Creates a request to change DataObjects with [objectChanges] in a Store.
 */
fun <DO: Any, P: PropertyDefinitions<DO>> RootDataModel<DO, P>.change(vararg objectChanges: DataObjectChange<DO>) =
    ChangeRequest(this, objectChanges.toList())

/** A Request to change DataObjects for [dataModel] with [objectChanges] */
data class ChangeRequest<DO: Any, out DM: RootDataModel<DO, *>> internal constructor(
    override val dataModel: DM,
    val objectChanges: List<DataObjectChange<DO>>
) : IsObjectRequest<DO, DM> {

    internal companion object: QueryDataModel<ChangeRequest<*, *>>(
        properties = object : PropertyDefinitions<ChangeRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, ChangeRequest<*, *>::dataModel)
                add(1, "objectChanges", ListDefinition(
                    valueDefinition = SubModelDefinition(
                        dataModel = { DataObjectChange }
                    )
                ), ChangeRequest<*, *>::objectChanges)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = ChangeRequest(
            dataModel = map[0] as RootDataModel<Any, *>,
            objectChanges = map[1] as List<DataObjectChange<Any>>
        )
    }
}
