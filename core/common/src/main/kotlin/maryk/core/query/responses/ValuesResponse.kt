package maryk.core.query.responses

import maryk.core.models.IsRootValuesDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
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
    internal companion object: SimpleQueryDataModel<ValuesResponse<*, *>>(
        properties = object : ObjectPropertyDefinitions<ValuesResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, ValuesResponse<*, *>::dataModel)
                add(2, "values", ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { ValuesWithMetaData }
                    )
                ), ValuesResponse<*, *>::values)
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ValuesResponse<*, *>>) = ValuesResponse(
            dataModel = map(1),
            values = map<List<ValuesWithMetaData<IsRootValuesDataModel<PropertyDefinitions>, PropertyDefinitions>>>(2)
        )
    }
}
