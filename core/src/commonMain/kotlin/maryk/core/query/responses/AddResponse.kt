package maryk.core.query.responses

import maryk.core.models.IsRootDataModel
import maryk.core.models.SimpleQueryDataModel
import maryk.core.values.SimpleObjectValues
import maryk.core.properties.IsPropertyDefinitions
import maryk.core.properties.ObjectPropertyDefinitions
import maryk.core.properties.types.TypedValue
import maryk.core.query.responses.statuses.IsAddResponseStatus
import maryk.core.query.responses.statuses.StatusType

/** Response with [statuses] to an Add request to [dataModel] */
data class AddResponse<DM: IsRootDataModel<*>> constructor(
    override val dataModel: DM,
    val statuses: List<IsAddResponseStatus<DM>>
): IsDataModelResponse<DM> {
    companion object: SimpleQueryDataModel<AddResponse<*>>(
        properties = object : ObjectPropertyDefinitions<AddResponse<*>>() {
            init {
                IsDataModelResponse.addDataModel(this, AddResponse<*>::dataModel)
                IsDataModelResponse.addStatuses(this) { response ->
                    response.statuses.map { TypedValue(it.statusType, it) }
                }
            }
        }
    ) {
        override fun invoke(map: SimpleObjectValues<AddResponse<*>>) = AddResponse(
            dataModel = map(1),
            statuses = map<List<TypedValue<StatusType, IsAddResponseStatus<IsRootDataModel<IsPropertyDefinitions>>>>?>(2)?.map { it.value } ?: emptyList()
        )
    }
}
