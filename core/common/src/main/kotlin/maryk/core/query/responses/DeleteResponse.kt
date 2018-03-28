package maryk.core.query.responses

import maryk.core.objects.QueryDataModel
import maryk.core.objects.RootDataModel
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsDeleteResponseStatus
import maryk.core.query.responses.statuses.StatusType

/** Response with [statuses] to a Delete request to [dataModel] */
data class DeleteResponse<DO: Any, out DM: RootDataModel<DO, *>>(
    override val dataModel: DM,
    val statuses: List<IsDeleteResponseStatus<DO>>
) : IsDataModelResponse<DO, DM> {
    internal companion object: QueryDataModel<DeleteResponse<*, *>>(
        properties = object : PropertyDefinitions<DeleteResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, DeleteResponse<*, *>::dataModel)
                IsDataModelResponse.addStatuses(this) {
                    it.statuses.map { TypedValue(it.statusType, it) }
                }
            }
        }
    ) {
        @Suppress("UNCHECKED_CAST")
        override fun invoke(map: Map<Int, *>) = DeleteResponse(
            dataModel = map[0] as RootDataModel<Any, *>,
            statuses = (map[1] as List<TypedValue<StatusType, IsDeleteResponseStatus<Any>>>?)?.map { it.value } ?: emptyList()
        )
    }
}
