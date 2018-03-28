package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualSubModelDefinition
import maryk.core.query.DataModelPropertyContext

/** A Request to add [objectsToAdd] to [dataModel] */
data class AddRequest<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    val objectsToAdd: List<DO>
) : IsObjectRequest<DO, DM> {
    constructor(dataModel: DM, vararg objectToAdd: DO) : this(dataModel, objectToAdd.toList())

    internal companion object: QueryDataModel<AddRequest<*, *>>(
        properties = object : PropertyDefinitions<AddRequest<*, *>>() {
            init {
                IsObjectRequest.addDataModel(this, AddRequest<*, *>::dataModel)
                add(1, "objectsToAdd", ListDefinition(
                    valueDefinition = ContextualSubModelDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            it?.dataModel ?: throw ContextNotFoundException()
                        }
                    )
                ), AddRequest<*, *>::objectsToAdd)
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = AddRequest(
            dataModel = map[0] as RootDataModel<Any, *>,
            objectsToAdd = map[1] as List<Any>
        )
    }
}
