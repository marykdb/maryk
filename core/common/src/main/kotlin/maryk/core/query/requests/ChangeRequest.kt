package maryk.core.query.requests

import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
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
    override val requestType = RequestType.Change

    internal companion object: SimpleQueryDataModel<ChangeRequest<*, *>>(
        properties = object : PropertyDefinitions<ChangeRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, ChangeRequest<*, *>::dataModel)
                add(1, "objectChanges",
                    ListDefinition(
                        valueDefinition = EmbeddedObjectDefinition(
                            dataModel = { DataObjectChange }
                        )
                    ),
                    getter = ChangeRequest<*, *>::objectChanges
                );
            }
        }
    ) {
        override fun invoke(map: SimpleValues<ChangeRequest<*, *>>) = ChangeRequest(
            dataModel = map<RootDataModel<Any, *>>(0),
            objectChanges = map(1)
        )
    }
}
