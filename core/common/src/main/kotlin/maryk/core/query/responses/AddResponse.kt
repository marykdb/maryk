package maryk.core.query.responses

import maryk.core.models.RootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.objects.ValueMap
import maryk.core.properties.definitions.PropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.StatusType

/** Response with [statuses] to an Add request to [dataModel] */
data class AddResponse<DO: Any, out DM: RootDataModel<DO, *>> constructor(
    override val dataModel: DM,
    val statuses: List<IsAddResponseStatus<DO>>
): IsDataModelResponse<DO, DM> {
    internal companion object: SimpleQueryDataModel<AddResponse<*, *>>(
        properties = object : PropertyDefinitions<AddResponse<*, *>>() {
            init {
                IsDataModelResponse.addDataModel(this, AddResponse<*, *>::dataModel)
                IsDataModelResponse.addStatuses(this) {
                    it.statuses.map { TypedValue(it.statusType, it) }
                }
            }
        }
    ) {
        override fun invoke(map: ValueMap<AddResponse<*, *>>) = AddResponse(
            dataModel = map(0),
            statuses = map<List<TypedValue<StatusType, IsAddResponseStatus<Any>>>?>(1)?.map { it.value } ?: emptyList()
        )
    }
}
