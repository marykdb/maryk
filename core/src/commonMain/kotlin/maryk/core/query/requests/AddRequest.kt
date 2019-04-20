package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsObjectDataModel
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.IsValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.models.ValuesDataModelImpl
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.IsValueDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.query.RequestContext
import maryk.core.query.responses.AddResponse
import maryk.core.values.ObjectValues
import maryk.core.values.Values

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.add(vararg objectToAdd: Values<DM, P>) =
    AddRequest(this, objectToAdd.toList())

/** A Request to add [objects] to [dataModel] */
data class AddRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    val objects: List<Values<DM, P>>
) : IsStoreRequest<DM, AddResponse<DM>> {
    override val requestType = RequestType.Add
    @Suppress("UNCHECKED_CAST")
    override val responseModel = AddResponse as IsObjectDataModel<AddResponse<DM>, *>

    object Properties : ObjectPropertyDefinitions<AddRequest<*, *>>() {
        val dataModel = IsObjectRequest.addDataModel("to", this, AddRequest<*, *>::dataModel)

        @Suppress("UNCHECKED_CAST")
        val objects = add(2u, "objects", ListDefinition(
            valueDefinition = ContextualEmbeddedValuesDefinition<RequestContext>(
                contextualResolver = {
                    it?.dataModel as? ValuesDataModelImpl<RequestContext>? ?: throw ContextNotFoundException()
                }
            ) as IsValueDefinition<Values<out IsValuesDataModel<*>, out PropertyDefinitions>, RequestContext>
        ), AddRequest<*, *>::objects)
    }

    companion object : QueryDataModel<AddRequest<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<AddRequest<*, *>, Properties>) =
            AddRequest<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>(
                dataModel = values(1u),
                objects = values(2u)
            )
    }
}
