package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.StatusType

/** Response with [statuses] to a Change request to [dataModel] */
data class ChangeResponse<DM: IsRootDataModel<*>>(
    override val dataModel: DM,
    val statuses: List<IsChangeResponseStatus<DM>>
) : IsDataModelResponse<DM> {
    companion object: SimpleQueryDataModel<ChangeResponse<*>>(
        properties = object : ObjectPropertyDefinitions<ChangeResponse<*>>() {
            init {
                IsDataModelResponse.addDataModel(this, ChangeResponse<*>::dataModel)
                IsDataModelResponse.addStatuses(this) { response ->
                    response.statuses.map { TypedValue(it.statusType, it) }
                }
            }
        }
    ) {
        override fun invoke(values: SimpleObjectValues<ChangeResponse<*>>) = ChangeResponse(
            dataModel = values(1),
            statuses = values<List<TypedValue<StatusType, IsChangeResponseStatus<IsRootDataModel<IsPropertyDefinitions>>>>?>(2)?.map { it.value } ?: emptyList()
        )
    }
}
