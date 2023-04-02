package maryk.core.query.responses

import maryk.core.properties.IsRootModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.values.SimpleObjectValues

/** Response with [statuses] to an Add request to [dataModel] */
data class AddResponse<DM : IsRootModel>(
    override val dataModel: DM,
    val statuses: List<IsAddResponseStatus<DM>>
) : IsDataModelResponse<DM> {
    companion object : SimpleQueryModel<AddResponse<*>>() {
        val dataModel by addDataModel({ it.dataModel })
        val statuses by list(
            index = 2u,
            getter = { response ->
                response.statuses.map { TypedValue(it.statusType, it) }
            },
            valueDefinition = statusesMultiType
        )

        override fun invoke(values: SimpleObjectValues<AddResponse<*>>) = AddResponse(
            dataModel = values(1u),
            statuses = values<List<TypedValue<TypeEnum<IsAddResponseStatus<IsRootModel>>, IsAddResponseStatus<IsRootModel>>>?>(2u)?.map { it.value } ?: emptyList()
        )
    }
}
