package maryk.core.query.requests

import maryk.core.objects.Def
import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.ListDefinition
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

    object Properties {
        val objectsToAdd = ListDefinition(
                name = "objectsToAdd",
                index = 1,
                required = true,
                valueDefinition = ContextualSubModelDefinition<DataModelPropertyContext>(
                        contextualResolver = { it!!.dataModel!! }
                )
        )
    }

    companion object: QueryDataModel<AddRequest<*, *>>(
            definitions = listOf(
                    Def(IsObjectRequest.Properties.dataModel, AddRequest<*, *>::dataModel),
                    Def(Properties.objectsToAdd, {
                        it.objectsToAdd.toList()
                    })
            )
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = AddRequest(
                dataModel = map[0] as RootDataModel<Any>,
                objectsToAdd = map[1] as List<Any>
        )
    }
}
