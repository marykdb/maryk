package maryk.core.query.responses

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.QueryDataModel
import maryk.core.objects.ObjectValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.PropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.ValuesWithMetaData

/** Response with [values] to an objects (Get/Scan) request to [dataModel] */
data class ValuesResponse<DM: IsRootValuesDataModel<P>, P: PropertyDefinitions>(
    override val dataModel: DM,
    val values: List<ValuesWithMetaData<DM, P>>
) : IsDataModelResponse<DM> {
    object Properties : ObjectPropertyDefinitions<ValuesResponse<*, *>>() {
        val dataModel = IsDataModelResponse.addDataModel(this, ValuesResponse<*, *>::dataModel)
        val values = add(2, "values", ListDefinition(
            valueDefinition = EmbeddedObjectDefinition(
                dataModel = { ValuesWithMetaData }
            )
        ), ValuesResponse<*, *>::values)
    }

    companion object: QueryDataModel<ValuesResponse<*, *>, Properties>(
        properties = Properties
    ) {
        override fun invoke(map: ObjectValues<ValuesResponse<*, *>, Properties>) = ValuesResponse(
            dataModel = map(1),
            values = map<List<ValuesWithMetaData<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>>(2)
        )
    }
}
