package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.models.ValuesDataModelImpl
import maryk.core.objects.ObjectValues
import maryk.core.objects.Values
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.query.RequestContext
import maryk.core.query.responses.AddResponse

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> DM.add(vararg objectToAdd: Values<DM, P>) =
    AddRequest(this, objectToAdd.toList())

/** A Request to add [objectsToAdd] to [dataModel] */
data class AddRequest<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    val objectsToAdd: List<Values<DM, P>>
) : IsObjectRequest<DM, AddResponse<*>> {
    override val requestType = RequestType.Add
    override val responseModel = AddResponse

    object Properties : ObjectPropertyDefinitions<AddRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel(this, AddRequest<*, *>::dataModel)

        @Suppress("UNCHECKED_CAST", "unused")
        val objectsToAdd = add(2, "objectsToAdd", ListDefinition(
            valueDefinition = ContextualEmbeddedValuesDefinition<RequestContext>(
                contextualResolver = {
                    @Suppress("UNCHECKED_CAST")
                    it?.dataModel as? ValuesDataModelImpl<RequestContext>? ?: throw ContextNotFoundException()
                }
            ) as IsValueDefinition<Values<out IsValuesDataModel<*>, out PropertyDefinitions>, RequestContext>
        ), AddRequest<*, *>::objectsToAdd)
    }

    companion object: QueryDataModel<AddRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<AddRequest<*, *>, Properties>) = AddRequest<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
            dataModel = map(1),
            objectsToAdd = map(2)
        )
    }
}