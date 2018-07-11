package maryk.core.query.requests

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.changes.DataObjectChange

/**
 * Creates a request to change DataObjects with [objectChanges] in a Store.
 */
fun <DM: IsRootDataModel<P>, P: ObjectPropertyDefinitions<*>> DM.change(vararg objectChanges: DataObjectChange<DM>) =
    ChangeRequest(this, objectChanges.toList())

/** A Request to change DataObjects for [dataModel] with [objectChanges] */
data class ChangeRequest<out DM: IsRootDataModel<*>> internal constructor(
    override val dataModel: DM,
    val objectChanges: List<DataObjectChange<DM>>
) : IsObjectRequest<DM> {
    override val requestType = RequestType.Change

    internal companion object: SimpleQueryDataModel<ChangeRequest<*>>(
        properties = object : ObjectPropertyDefinitions<ChangeRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, ChangeRequest<*>::dataModel)
                add(1, "objectChanges",
                    ListDefinition(
                        valueDefinition = EmbeddedObjectDefinition(
                            dataModel = { DataObjectChange }
                        )
                    ),
                    getter = ChangeRequest<*>::objectChanges
                )
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ChangeRequest<*>>) = ChangeRequest(
            dataModel = map(0),
            objectChanges = map(1)
        )
    }
}
