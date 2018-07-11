package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.RootObjectDataModel
import maryk.core.models.SimpleObjectDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedObjectDefinition
import maryk.core.query.DataModelPropertyContext

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DO: Any, P: ObjectPropertyDefinitions<DO>> RootObjectDataModel<*, DO, P>.add(vararg objectToAdd: DO) =
    AddRequest(this, objectToAdd.toList())

/** A Request to add [objectsToAdd] to [dataModel] */
data class AddRequest<DO: Any, out DM: RootObjectDataModel<*, DO, *>> internal constructor(
    override val dataModel: DM,
    val objectsToAdd: List<DO>
) : IsObjectRequest<DM> {
    override val requestType = RequestType.Add

    internal companion object: SimpleQueryDataModel<AddRequest<*, *>>(
        properties = object : ObjectPropertyDefinitions<AddRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, AddRequest<*, *>::dataModel)
                add(1, "objectsToAdd", ListDefinition(
                    valueDefinition = ContextualEmbeddedObjectDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            @Suppress("UNCHECKED_CAST")
                            it?.dataModel as? SimpleObjectDataModel<Any, ObjectPropertyDefinitions<Any>>? ?: throw ContextNotFoundException()
                        }
                    )
                ), AddRequest<*, *>::objectsToAdd)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<AddRequest<*, *>>) = AddRequest(
            dataModel = map<RootObjectDataModel<*, Any, *>>(0),
            objectsToAdd = map(1)
        )
    }
}
