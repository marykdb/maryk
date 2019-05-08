package maryk.core.query.responses

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.wrapper.ObjectListDefinitionWrapper
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.ObjectValues

/** Response with [values] to an objects (Get/Scan) request to [dataModel] */
data class ValuesResponse<DM : IsRootValuesDataModel<P>, P : PropertyDefinitions>(
    override val dataModel: DM,
    val values: List<ValuesWithMetaData<DM, P>>
) : IsDataModelResponse<DM> {
    object Properties : ObjectPropertyDefinitions<ValuesResponse<*, *>>() {
        val dataModel = IsDataModelResponse.addDataModel(this, ValuesResponse<*, *>::dataModel)
        val values = ObjectListDefinitionWrapper(
            2u, "values",
            properties = ValuesWithMetaData.Properties,
            definition = ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ValuesWithMetaData }
                )
            ),
            getter = ValuesResponse<*, *>::values
        ).also {
            addSingle(it)
        }
    }

    companion object : QueryDataModel<ValuesResponse<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(values: ObjectValues<ValuesResponse<*, *>, Properties>) = ValuesResponse(
            dataModel = values(1u),
            values = values<List<ValuesWithMetaData<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>>(2u)
        )
    }
}
