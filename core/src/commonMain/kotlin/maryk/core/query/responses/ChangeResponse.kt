package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.values.SimpleObjectValues

/** Response with [statuses] to a Change request to [dataModel] */
data class ChangeResponse<DM : IsRootDataModel<*>>(
    override val dataModel: DM,
    val statuses: List<IsChangeResponseStatus<DM>>
) : IsDataModelResponse<DM> {
    @Suppress("unused")
    companion object : SimpleQueryDataModel<ChangeResponse<*>>(
        properties = object : ObjectPropertyDefinitions<ChangeResponse<*>>() {
            val dataModel by addDataModel(ChangeResponse<*>::dataModel)
            val statuses by list(
                index = 2u,
                getter = { response ->
                    response.statuses.map { TypedValue(it.statusType, it) }
                },
                valueDefinition = statusesMultiType
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ChangeResponse<*>>) = ChangeResponse(
            dataModel = values(1u),
            statuses = values<List<TypedValue<TypeEnum<IsChangeResponseStatus<IsRootDataModel<IsPropertyDefinitions>>>, IsChangeResponseStatus<IsRootDataModel<IsPropertyDefinitions>>>>?>(2u)?.map { it.value } ?: emptyList()
        )
    }
}
