package maryk.core.query.responses

import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.SimpleValues
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.StatusType

/** Response with [statuses] to a Delete request to [dataModel] */
data class DeleteResponse<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    val statuses: List<IsDeleteResponseStatus<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: SimpleQueryDataModel<DeleteResponse<*, *>>(
        properties = object : ObjectPropertyDefinitions<DeleteResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, DeleteResponse<*, *>::dataModel)
                IsDataModelResponse.addStatuses(this) {
                    it.statuses.map { TypedValue(it.statusType, it) }
                }
            }
        }
    ) {
        override fun invoke(map: SimpleValues<DeleteResponse<*, *>>) = DeleteResponse(
            dataModel = map(0),
            statuses = map<List<TypedValue<StatusType, IsDeleteResponseStatus<Any>>>?>(1)?.map { it.value } ?: emptyList()
        )
    }
}
