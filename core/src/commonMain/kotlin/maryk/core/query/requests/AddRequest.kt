package maryk.core.query.requests

import maryk.core.exceptions.ContextNotFoundException
import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.PropertyBaseRootDataModel
import maryk.core.models.QueryDataModel
import maryk.core.models.ValuesDataModelImpl
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.RootModel
import maryk.core.properties.definitions.contextual.ContextualEmbeddedValuesDefinition
import maryk.core.properties.definitions.list
import maryk.core.query.RequestContext
import maryk.core.query.requests.RequestType.Add
import maryk.core.query.responses.AddResponse
import maryk.core.values.ObjectValues
import maryk.core.values.Values

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> DM.add(vararg objectToAdd: Values<DM, P>) =
    AddRequest(this, objectToAdd.toList())

/** Creates a Request to add multiple [objectToAdd] to a store defined by given DataModel */
fun <DM : RootModel<P>, P : PropertyDefinitions> DM.add(vararg objectToAdd: Values<PropertyBaseRootDataModel<P>, P>) =
    AddRequest(this.Model, objectToAdd.toList())

/** A Request to add [objects] to [dataModel] */
data class AddRequest<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions> internal constructor(
    override val dataModel: DM,
    val objects: List<Values<DM, P>>
) : IsStoreRequest<DM, AddResponse<DM>>, IsTransportableRequest<AddResponse<DM>> {
    override val requestType = Add
    override val responseModel = AddResponse

    object Properties : ObjectPropertyDefinitions<AddRequest<*, *>>() {
        val to by addDataModel(AddRequest<*, *>::dataModel)

        val objects by list(
            index = 2u,
            getter = AddRequest<*, *>::objects,
            valueDefinition = ContextualEmbeddedValuesDefinition<RequestContext>(
                contextualResolver = {
                    @Suppress("UNCHECKED_CAST")
                    it?.dataModel as? ValuesDataModelImpl<RequestContext>? ?: throw ContextNotFoundException()
                }
            )
        )
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
