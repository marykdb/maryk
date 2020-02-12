package maryk.core.query.responses

import maryk.core.aggregations.AggregationsResponse
import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.EmbeddedObjectDefinition
import maryk.core.properties.definitions.embedObject
import maryk.core.properties.definitions.list
import maryk.core.query.changes.DataObjectVersionedChange
import maryk.core.values.SimpleObjectValues

/** Response with [changes] with all versioned changes since version in request to [dataModel] */
data class ChangesResponse<out DM : IsRootDataModel<*>>(
    override val dataModel: DM,
    val changes: List<DataObjectVersionedChange<DM>>,
    val aggregations: AggregationsResponse? = null
) : IsDataModelResponse<DM> {
    @Suppress("unused")
    companion object : SimpleQueryDataModel<ChangesResponse<*>>(
        properties = object : ObjectPropertyDefinitions<ChangesResponse<*>>() {
            val dataModel by addDataModel(ChangesResponse<*>::dataModel)
            val changes by list(
                index = 2u,
                getter = ChangesResponse<*>::changes,
                valueDefinition = EmbeddedObjectDefinition(
                    dataModel = { DataObjectVersionedChange }
                )
            )

            val aggregations by embedObject(
                index = 3u,
                getter = ChangesResponse<*>::aggregations,
                dataModel = { AggregationsResponse },
                alternativeNames = setOf("aggs")
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ChangesResponse<*>>) = ChangesResponse(
            dataModel = values(1u),
            changes = values(2u),
            aggregations = values(3u)
        )
    }
}
