package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.values.SimpleObjectValues

/** Response with [statuses] to an Add request to [dataModel] */
data class AddResponse<DM : IsRootDataModel<*>> constructor(
    override val dataModel: DM,
    val statuses: List<IsAddResponseStatus<DM>>
) : IsDataModelResponse<DM> {
    @Suppress("unused")
    companion object : SimpleQueryDataModel<AddResponse<*>>(
        properties = object : ObjectPropertyDefinitions<AddResponse<*>>() {
            val dataModel by addDataModel(AddResponse<*>::dataModel)
            val statuses by list(
                index = 2u,
                getter = { response ->
                    response.statuses.map { TypedValue(it.statusType, it) }
                },
                valueDefinition = statusesMultiType
            )
        }
    ) {
        override fun invoke(values: SimpleObjectValues<AddResponse<*>>) = AddResponse(
            dataModel = values(1u),
            statuses = values<List<TypedValue<TypeEnum<IsAddResponseStatus<IsRootDataModel<IsPropertyDefinitions>>>, IsAddResponseStatus<IsRootDataModel<IsPropertyDefinitions>>>>?>(2u)?.map { it.value } ?: emptyList()
        )
    }
}
