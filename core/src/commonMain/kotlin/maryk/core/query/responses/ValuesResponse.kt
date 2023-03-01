package maryk.core.query.responses

import maryk.core.aggregations.AggregationsResponse
import maryk.core.properties.IsRootModel
import maryk.core.properties.QueryModel
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.wrapper.ObjectListDefinitionWrapper
import maryk.core.query.ValuesWithMetaData
import maryk.core.values.ObjectValues

/** Response with [values] to an objects (Get/Scan) request to [dataModel] */
data class ValuesResponse<DM : IsRootModel>(
    override val dataModel: DM,
    val values: List<ValuesWithMetaData<DM>>,
    val aggregations: AggregationsResponse? = null
) : IsDataResponse<DM> {
    companion object : QueryModel<ValuesResponse<*>, Companion>() {
        val dataModel by addDataModel({ it.dataModel })
        val values = ObjectListDefinitionWrapper(
            2u, "values",
            properties = ValuesWithMetaData.Properties,
            definition = ListDefinition(
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { ValuesWithMetaData }
                )
            ),
            getter = ValuesResponse<*>::values
        ).also(::addSingle)

        val aggregations by embedObject(
            index = 3u,
            getter = ValuesResponse<*>::aggregations,
            dataModel = { AggregationsResponse },
            alternativeNames = setOf("aggs")
        )

        override fun invoke(values: ObjectValues<ValuesResponse<*>, Companion>) = ValuesResponse(
            dataModel = values(1u),
            values = values(2u),
            aggregations = values(3u)
        )
    }
}
