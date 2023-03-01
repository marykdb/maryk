package maryk.core.query.responses

import maryk.core.properties.IsRootModel
import maryk.core.properties.SimpleQueryModel
import maryk.core.properties.definitions.list
import maryk.core.properties.enum.TypeEnum
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsAddOrChangeResponseStatus
import maryk.core.values.SimpleObjectValues

/**
 * Response with [statuses] to a Change request to [dataModel]
 * It will contain an AddResponseStatus if the change contained an ObjectCreate object
 */
data class AddOrChangeResponse<DM : IsRootModel>(
    override val dataModel: DM,
    val statuses: List<IsAddOrChangeResponseStatus<DM>>
) : IsDataModelResponse<DM> {
    companion object : SimpleQueryModel<AddOrChangeResponse<*>>() {
        val dataModel by addDataModel({ it.dataModel })
        val statuses by list(
            index = 2u,
            getter = { response ->
                response.statuses.map { TypedValue(it.statusType, it) }
            },
            valueDefinition = statusesMultiType
        )

        override fun invoke(values: SimpleObjectValues<AddOrChangeResponse<*>>) = AddOrChangeResponse(
            dataModel = values(1u),
            statuses = values<List<TypedValue<TypeEnum<IsAddOrChangeResponseStatus<IsRootModel>>, IsAddOrChangeResponseStatus<IsRootModel>>>?>(2u)?.map { it.value } ?: emptyList()
        )
    }
}
