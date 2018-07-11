package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleObjectValues
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
    internal companion object: SimpleQueryDataModel<ChangeResponse<*>>(
        properties = object : ObjectPropertyDefinitions<ChangeResponse<*>>() {
            init {
                IsDataModelResponse.addDataModel(this, ChangeResponse<*>::dataModel)
                IsDataModelResponse.addStatuses(this) { response ->
                    response.statuses.map { TypedValue(it.statusType, it) }
                }
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<ChangeResponse<*>>) = ChangeResponse(
            dataModel = map(0),
            statuses = map<List<TypedValue<StatusType, IsChangeResponseStatus<IsRootDataModel<IsPropertyDefinitions>>>>?>(1)?.map { it.value } ?: emptyList()
        )
    }
}
