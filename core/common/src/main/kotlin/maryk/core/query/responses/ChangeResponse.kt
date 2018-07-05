package maryk.core.query.responses

import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsChangeResponseStatus
import maryk.core.query.responses.statuses.StatusType

/** Response with [statuses] to a Change request to [dataModel] */
data class ChangeResponse<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    val statuses: List<IsChangeResponseStatus<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: SimpleQueryDataModel<ChangeResponse<*, *>>(
        properties = object : PropertyDefinitions<ChangeResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, ChangeResponse<*, *>::dataModel)
                IsDataModelResponse.addStatuses(this) {
                    it.statuses.map { TypedValue(it.statusType, it) }
                }
            }
        }
    ) {
        override fun invoke(map: SimpleValues<ChangeResponse<*, *>>) = ChangeResponse(
            dataModel = map(0),
            statuses = map<List<TypedValue<StatusType, IsChangeResponseStatus<Any>>>?>(1)?.map { it.value } ?: emptyList()
        )
    }
}
