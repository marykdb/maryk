package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.models.ValuesDataModelImpl
import maryk.core.objects.SimpleObjectValues
import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.query.DataModelPropertyContext

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DM: IsRootValuesDataModel<*>> DM.add(vararg objectToAdd: Values<DM, *>) =
    AddRequest(this, objectToAdd.toList())

/** A Request to add [objectsToAdd] to [dataModel] */
data class AddRequest<DM: IsRootValuesDataModel<*>> internal constructor(
    override val dataModel: DM,
    val objectsToAdd: List<Values<DM, *>>
) : IsObjectRequest<DM> {
    override val requestType = RequestType.Add

    internal companion object: SimpleQueryDataModel<AddRequest<*>>(
        properties = object : ObjectPropertyDefinitions<AddRequest<*>>() {
            init {
                IsObjectRequest.addDataModel(this, AddRequest<*>::dataModel)
                @Suppress("UNCHECKED_CAST")
                add(1, "objectsToAdd", ListDefinition(
                    valueDefinition = ContextualEmbeddedValuesDefinition<DataModelPropertyContext>(
                        contextualResolver = {
                            @Suppress("UNCHECKED_CAST")
                            it?.dataModel as? ValuesDataModelImpl<DataModelPropertyContext>? ?: throw ContextNotFoundException()
                        }
                    ) as IsValueDefinition<Values<out IsValuesDataModel<*>, out PropertyDefinitions>, DataModelPropertyContext>
                ), AddRequest<*>::objectsToAdd)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<AddRequest<*>>) = AddRequest(
            dataModel = map(0),
            objectsToAdd = map(1)
        )
    }
}
