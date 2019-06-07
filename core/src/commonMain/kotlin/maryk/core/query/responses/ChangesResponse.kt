package maryk.core.query.responses

import maryk.core.aggregations.AggregationsResponse
import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.ListDefinition
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.values.SimpleObjectValues

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class ChangesResponse<out DM : IsRootDataModel<*>>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DM>>,
    val aggregations: AggregationsResponse? = null
) : IsDataModelResponse<DM> {
    companion object : SimpleQueryDataModel<ChangesResponse<*>>(
        properties = object : ObjectPropertyDefinitions<ChangesResponse<*>>() {
            init {
                IsDataModelResponse.addDataModel(this, ChangesResponse<*>::dataModel)
                add(2u, "changes", ListDefinition(
                    valueDefinition = EmbeddedObjectDefinition(
                        dataModel = { DataObjectVersionedChange }
                    )
                ), ChangesResponse<*>::changes)

                add(
                    3u,
                    "aggregations",
                    EmbeddedObjectDefinition(dataModel = { AggregationsResponse }),
                    ChangesResponse<*>::aggregations,
                    alternativeNames = setOf("aggs")
                )
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ChangesResponse<*>>) = ChangesResponse(
            dataModel = values(1u),
            changes = values(2u),
            aggregations = values(3u)
        )
    }
}
