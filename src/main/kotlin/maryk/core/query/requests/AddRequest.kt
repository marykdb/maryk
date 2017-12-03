package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.definitions.contextual.ContextualSubModelDefinition
import maryk.core.query.DataModelPropertyContext

/** A Request to add DataObjects for specific DataModel
 * @param dataModel Root model of data to add objects to
 * @param objectsToAdd Array of objects to add
 */
data class AddRequest<DO: Any, out DM: RootDataModel<DO>>(
        override val dataModel: DM,
        val objectsToAdd: List<DO>
) : IsObjectRequest<DO, DM> {
    constructor(dataModel: DM, vararg objectToAdd: DO) : this(dataModel, objectToAdd.toList())

    internal object Properties : PropertyDefinitions<AddRequest<*, *>>() {
        val objectsToAdd = add(1, "objectsToAdd",ListDefinition(
                name = "objectsToAdd",
                index = 1,
                required = true,
                valueDefinition = ContextualSubModelDefinition<DataModelPropertyContext>(
                        contextualResolver = { it!!.dataModel!! }
                )
        ), AddRequest<*, *>::objectsToAdd)
    }

    companion object: QueryDataModel<AddRequest<*, *>>(
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, AddRequest<*, *>::dataModel),
                    Def(Properties.objectsToAdd, {
                        it.objectsToAdd.toList()
                    })
            ),
            properties = object : PropertyDefinitions<AddRequest<*, *>>() {
                init {
                    IsObjectRequest.addDataModel(this, AddRequest<*, *>::dataModel)
                    add(1, "objectsToAdd", ListDefinition(
                            required = true,
                            valueDefinition = ContextualSubModelDefinition<DataModelPropertyContext>(
                                    contextualResolver = { it!!.dataModel!! }
                            )
                    ), AddRequest<*, *>::objectsToAdd)
                }
            }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = AddRequest(
                dataModel = map[0] as RootDataModel<Any>,
                objectsToAdd = map[1] as List<Any>
        )
    }
}
