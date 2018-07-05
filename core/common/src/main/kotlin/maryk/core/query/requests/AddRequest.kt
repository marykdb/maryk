package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.query.DataModelPropertyContext

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DO: Any, P: PropertyDefinitions<DO>> RootDataModel<DO, P>.add(vararg objectToAdd: DO) =
    AddRequest(this, objectToAdd.toList())

/** A Request to add [objectsToAdd] to [dataModel] */
data class AddRequest<DO: Any, out DM: RootDataModel<DO, *>> internal constructor(
    override val dataModel: DM,
    val objectsToAdd: List<DO>
) : IsObjectRequest<DO, DM> {
    override val requestType = RequestType.Add

    internal companion object: SimpleQueryDataModel<AddRequest<*, *>>(
        properties = object : PropertyDefinitions<AddRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, AddRequest<*, *>::dataModel)
                add(1, "objectsToAdd", ListDefinition(
                    valueDefinition = ContextualEmbeddedObjectDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.dataModel ?: throw ContextNotFoundException()
                        }
                    )
                ), AddRequest<*, *>::objectsToAdd)
            }
        }
    ) {
        override fun invoke(map: SimpleValues<AddRequest<*, *>>) = AddRequest(
            dataModel = map<RootDataModel<Any, *>>(0),
            objectsToAdd = map(1)
        )
    }
}
