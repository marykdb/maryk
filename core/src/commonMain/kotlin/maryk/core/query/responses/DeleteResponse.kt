package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsValuesPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.values.SimpleObjectValues

/** Response with [statuses] to a Delete request to [dataModel] */
data class DeleteResponse<DM : IsRootDataModel<*>>(
    override val dataModel: DM,
    val statuses: List<IsDeleteResponseStatus<DM>>
) : IsDataModelResponse<DM> {
    @Suppress("unused")
    companion object : SimpleQueryDataModel<DeleteResponse<*>>(
        properties = object : ObjectPropertyDefinitions<DeleteResponse<*>>() {
            val dataModel by addDataModel(DeleteResponse<*>::dataModel)
            val statuses by list(
                index = 2u,
                getter = { response ->
                    response.statuses.map { TypedValue(it.statusType, it) }
                },
                valueDefinition = statusesMultiType
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<DeleteResponse<*>>) = DeleteResponse(
            dataModel = values(1u),
            statuses = values<List<TypedValue<TypeEnum<IsDeleteResponseStatus<IsRootDataModel<IsValuesPropertyDefinitions>>>, IsDeleteResponseStatus<IsRootDataModel<IsValuesPropertyDefinitions>>>>?>(2u)?.map { it.value } ?: emptyList()
        )
    }
}
